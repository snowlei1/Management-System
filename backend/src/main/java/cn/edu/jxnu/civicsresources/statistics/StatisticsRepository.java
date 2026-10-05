package cn.edu.jxnu.civicsresources.statistics;

import static cn.edu.jxnu.civicsresources.statistics.StatisticsOverview.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StatisticsRepository {
    private static final String PUBLISHED = "r.status='APPROVED' AND r.deleted_at IS NULL AND r.published_at IS NOT NULL";
    private final JdbcTemplate jdbc;
    public StatisticsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Users users() {
        return jdbc.queryForObject("""
            SELECT COUNT(*) total, COALESCE(SUM(o.code='ADMIN'),0) admin,
              COALESCE(SUM(o.code='TEACHER'),0) teacher, COALESCE(SUM(o.code='STUDENT'),0) student,
              COALESCE(SUM(u.status='ACTIVE'),0) active, COALESCE(SUM(u.status='DISABLED'),0) disabled
            FROM app_user u JOIN role o ON o.id=u.role_id
            """, (rs,n) -> new Users(rs.getLong("total"),rs.getLong("admin"),rs.getLong("teacher"),
                rs.getLong("student"),rs.getLong("active"),rs.getLong("disabled")));
    }
    public BaseCount courses() { return base("course"); }
    public BaseCount elements() { return base("ideological_element"); }
    public BaseCount categories() { return base("resource_category"); }
    // Table names are fixed internal constants, never request input.
    private BaseCount base(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) total,COALESCE(SUM(status='ACTIVE'),0) active FROM " + table,
                (rs,n) -> new BaseCount(rs.getLong("total"),rs.getLong("active")));
    }
    public Resources resources() {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(deleted_at IS NULL),0) total,
              COALESCE(SUM(deleted_at IS NULL AND status='DRAFT'),0) draft,
              COALESCE(SUM(deleted_at IS NULL AND status='PENDING'),0) pending,
              COALESCE(SUM(deleted_at IS NULL AND status='REJECTED'),0) rejected,
              COALESCE(SUM(deleted_at IS NULL AND status='APPROVED' AND published_at IS NOT NULL),0) approved,
              COALESCE(SUM(deleted_at IS NOT NULL),0) deleted FROM teaching_resource
            """, (rs,n) -> new Resources(rs.getLong("total"),rs.getLong("draft"),rs.getLong("pending"),
                rs.getLong("rejected"),rs.getLong("approved"),rs.getLong("deleted")));
    }
    public Usage usage() {
        return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM browse_record) browse_events,"
                + "(SELECT COUNT(*) FROM download_record) download_requests,"
                + "(SELECT COUNT(*) FROM favorite f JOIN teaching_resource r ON r.id=f.resource_id WHERE f.active=TRUE AND "
                + PUBLISHED + ") current_favorites", (rs,n) -> new Usage(rs.getLong("browse_events"),
                    rs.getLong("download_requests"),rs.getLong("current_favorites")));
    }
    public List<Distribution> courseDistribution() { return distribution("course", "course_id"); }
    public List<Distribution> categoryDistribution() { return distribution("resource_category", "category_id"); }
    private List<Distribution> distribution(String table, String foreignKey) {
        return jdbc.query("SELECT b.id,b.name,b.status,COUNT(r.id) resource_count FROM " + table
                + " b LEFT JOIN teaching_resource r ON r." + foreignKey + "=b.id AND " + PUBLISHED
                + " GROUP BY b.id,b.name,b.status ORDER BY resource_count DESC,b.id ASC", StatisticsRepository::map);
    }
    public List<Distribution> elementDistribution() {
        return jdbc.query("SELECT b.id,b.name,b.status,COUNT(r.id) resource_count FROM ideological_element b "
                + "LEFT JOIN resource_element_relation x ON x.element_id=b.id "
                + "LEFT JOIN teaching_resource r ON r.id=x.resource_id AND " + PUBLISHED
                + " GROUP BY b.id,b.name,b.status ORDER BY resource_count DESC,b.id ASC", StatisticsRepository::map);
    }
    private static Distribution map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Distribution(rs.getLong("id"),rs.getString("name"),rs.getString("status"),rs.getLong("resource_count"));
    }
}
