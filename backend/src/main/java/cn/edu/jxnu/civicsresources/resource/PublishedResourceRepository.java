package cn.edu.jxnu.civicsresources.resource;

import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublishedResourceRepository {
    private static final String VISIBLE = "r.status='APPROVED' AND r.deleted_at IS NULL AND r.published_at IS NOT NULL";
    private static final String FROM = """
        FROM teaching_resource r JOIN course c ON c.id=r.course_id
        JOIN resource_category k ON k.id=r.category_id JOIN app_user u ON u.id=r.created_by
        LEFT JOIN favorite f ON f.resource_id=r.id AND f.user_id=:user
        """;
    private static final String SELECT = """
        SELECT r.*,c.name AS course_name,c.status AS course_status,k.name AS category_name,
        k.status AS category_status,u.display_name AS teacher_name,COALESCE(f.active,0) AS is_favorite
        """ + FROM;
    private final NamedParameterJdbcTemplate jdbc;
    private final ResourceDraftRepository metadata;
    public PublishedResourceRepository(NamedParameterJdbcTemplate jdbc, ResourceDraftRepository metadata) {
        this.jdbc = jdbc; this.metadata = metadata;
    }
    public record Row(TeachingResource resource, boolean favorite) { }

    public boolean lockVisible(long id) {
        // Do not lock favorite rows while upgrading their state: concurrent idempotent UPSERTs handle uniqueness.
        return !jdbc.query("SELECT r.id FROM teaching_resource r WHERE r.id=:id AND " + VISIBLE + " FOR SHARE",
                Map.of("id", id), (rs, n) -> rs.getLong(1)).isEmpty();
    }
    public Optional<Row> findVisible(long id, long user) {
        return jdbc.query(SELECT + " WHERE r.id=:id AND " + VISIBLE, Map.of("id", id, "user", user),
                (rs, n) -> new Row(ResourceDraftRepository.map(rs), rs.getBoolean("is_favorite"))).stream().findFirst();
    }
    public PublishedResourcePage page(long user, String keyword, Long course, Long category, Long element,
            boolean favoritesOnly, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE " + VISIBLE);
        var p = new MapSqlParameterSource("user", user);
        if (favoritesOnly) where.append(" AND f.active=TRUE");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (r.title LIKE :keyword ESCAPE '!' OR r.description LIKE :keyword ESCAPE '!')");
            p.addValue("keyword", "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (course != null) { where.append(" AND r.course_id=:course"); p.addValue("course", course); }
        if (category != null) { where.append(" AND r.category_id=:category"); p.addValue("category", category); }
        if (element != null) {
            where.append(" AND EXISTS (SELECT 1 FROM resource_element_relation x WHERE x.resource_id=r.id AND x.element_id=:element)");
            p.addValue("element", element);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, p, Long.class);
        p.addValue("limit", size).addValue("offset", ((long) page - 1) * size);
        List<Row> rows = jdbc.query(SELECT + where + " ORDER BY r.published_at DESC,r.id DESC LIMIT :limit OFFSET :offset", p,
                (rs, n) -> new Row(ResourceDraftRepository.map(rs), rs.getBoolean("is_favorite")));
        var elements = metadata.elementsFor(rows.stream().map(row -> row.resource().id()).toList());
        return new PublishedResourcePage(rows.stream().map(row -> PublishedResourceView.from(row,
                elements.getOrDefault(row.resource().id(), List.of()))).toList(), total == null ? 0 : total, page, size);
    }
    public PublishedResourceView view(Row row) { return PublishedResourceView.from(row, metadata.elements(row.resource().id())); }
    public void browse(long user, long resource) {
        jdbc.update("INSERT INTO browse_record(user_id,resource_id) VALUES (:user,:resource)", Map.of("user", user, "resource", resource));
    }
    public void download(long user, long resource) {
        jdbc.update("INSERT INTO download_record(user_id,resource_id) VALUES (:user,:resource)", Map.of("user", user, "resource", resource));
    }
    public void favorite(long user, long resource, boolean active) {
        var p = Map.of("user", user, "resource", resource);
        if (active) jdbc.update("""
                INSERT INTO favorite(user_id,resource_id,active) VALUES (:user,:resource,TRUE)
                ON DUPLICATE KEY UPDATE active=TRUE
                """, p);
        else jdbc.update("UPDATE favorite SET active=FALSE WHERE user_id=:user AND resource_id=:resource", p);
    }
}
