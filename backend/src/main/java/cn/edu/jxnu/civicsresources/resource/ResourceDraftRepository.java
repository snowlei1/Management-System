package cn.edu.jxnu.civicsresources.resource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ResourceDraftRepository {
    private static final String SELECT = """
        SELECT r.*,c.name AS course_name,c.status AS course_status,
        k.name AS category_name,k.status AS category_status,u.display_name AS teacher_name
        FROM teaching_resource r JOIN course c ON c.id=r.course_id
        JOIN resource_category k ON k.id=r.category_id
        JOIN app_user u ON u.id=r.created_by
        """;
    private final NamedParameterJdbcTemplate jdbc;
    public ResourceDraftRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<TeachingResource> findOwned(long id, long owner, boolean lock) {
        return jdbc.query(SELECT + " WHERE r.id=:id AND r.created_by=:owner AND r.deleted_at IS NULL"
                + (lock ? " FOR UPDATE" : ""), Map.of("id", id, "owner", owner), (rs, row) -> map(rs))
                .stream().findFirst();
    }

    public Optional<TeachingResource> findForReview(long id, boolean lock) {
        return jdbc.query(SELECT + " WHERE r.id=:id AND r.deleted_at IS NULL" + (lock ? " FOR UPDATE" : ""),
                Map.of("id", id), (rs, row) -> map(rs)).stream().findFirst();
    }

    public ResourceDraftPage page(Long owner, String keyword, Long course, Long category, String status,
            boolean review, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE r.deleted_at IS NULL");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (owner != null) { where.append(" AND r.created_by=:owner"); p.addValue("owner", owner); }
        if (review) where.append(" AND r.status IN ('PENDING','REJECTED','APPROVED') AND r.submission_no>0");
        if (status != null && !status.isBlank()) { where.append(" AND r.status=:status"); p.addValue("status", status); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND r.title LIKE :keyword ESCAPE '!'");
            p.addValue("keyword", "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (course != null) { where.append(" AND r.course_id=:course"); p.addValue("course", course); }
        if (category != null) { where.append(" AND r.category_id=:category"); p.addValue("category", category); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM teaching_resource r" + where, p, Long.class);
        p.addValue("limit", size).addValue("offset", ((long)page - 1) * size);
        List<TeachingResource> rows = jdbc.query(SELECT + where + " ORDER BY r.id DESC LIMIT :limit OFFSET :offset", p, (rs, row) -> map(rs));
        Map<Long, List<ResourceElementView>> elements = elementsFor(rows.stream().map(TeachingResource::id).toList());
        return new ResourceDraftPage(rows.stream().map(r -> ResourceDraftView.from(r, elements.getOrDefault(r.id(), List.of()))).toList(),
                total == null ? 0 : total, page, size);
    }

    public List<ResourceElementView> elements(long resourceId) {
        return elementsFor(List.of(resourceId)).getOrDefault(resourceId, List.of());
    }

    Map<Long, List<ResourceElementView>> elementsFor(List<Long> ids) {
        Map<Long, List<ResourceElementView>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        jdbc.query("""
                SELECT x.resource_id,e.id,e.name,e.status FROM resource_element_relation x
                JOIN ideological_element e ON e.id=x.element_id
                WHERE x.resource_id IN (:ids) ORDER BY e.id
                """, Map.of("ids", ids), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.computeIfAbsent(rs.getLong("resource_id"), ignored -> new ArrayList<>())
                        .add(new ResourceElementView(rs.getLong("id"), rs.getString("name"), rs.getString("status"))));
        return result;
    }

    public boolean activeCourse(long id) { return active("course", id); }
    public boolean activeCategory(long id) { return active("resource_category", id); }
    public boolean activeElement(long id) { return active("ideological_element", id); }
    private boolean active(String fixedTable, long id) {
        // Lock the referenced row through transaction completion to serialize administrator deactivation.
        return jdbc.query("SELECT status FROM " + fixedTable + " WHERE id=:id FOR SHARE", Map.of("id", id),
                (rs, row) -> rs.getString("status")).stream().anyMatch("ACTIVE"::equals);
    }

    public long insert(long owner, ResourceDraftRequest r, StoredResourceFile file) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
            INSERT INTO teaching_resource (title,description,course_id,category_id,created_by,
            file_storage_key,file_original_name,file_mime_type,file_size_bytes,status)
            VALUES (:title,:description,:course,:category,:owner,:key,:original,:mime,:bytes,'DRAFT')
            """, params(r, file).addValue("owner", owner), key, new String[]{"id"});
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public void update(long id, ResourceDraftRequest r, StoredResourceFile file) {
        jdbc.update("""
            UPDATE teaching_resource SET title=:title,description=:description,course_id=:course,
            category_id=:category,file_storage_key=:key,file_original_name=:original,
            file_mime_type=:mime,file_size_bytes=:bytes,updated_at=CURRENT_TIMESTAMP(3)
            WHERE id=:id
            """, params(r, file).addValue("id", id));
    }

    public void replaceElements(long id, List<Long> elements) {
        jdbc.update("DELETE FROM resource_element_relation WHERE resource_id=:id", Map.of("id", id));
        for (long element : elements) jdbc.update("INSERT INTO resource_element_relation (resource_id,element_id) VALUES (:id,:element)",
                Map.of("id", id, "element", element));
    }
    public void softDelete(long id) {
        jdbc.update("UPDATE teaching_resource SET deleted_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=:id", Map.of("id", id));
    }
    public int submit(long id, String previousStatus) {
        return jdbc.update("""
                UPDATE teaching_resource SET status='PENDING',submission_no=submission_no+1,
                updated_at=CURRENT_TIMESTAMP(3) WHERE id=:id AND status=:previous AND deleted_at IS NULL
                """, Map.of("id", id, "previous", previousStatus));
    }
    public int decide(long id, long round, boolean approve) {
        return jdbc.update("UPDATE teaching_resource SET status=:status,published_at="
                + (approve ? "CURRENT_TIMESTAMP(3)" : "NULL")
                + ",updated_at=CURRENT_TIMESTAMP(3) WHERE id=:id AND status='PENDING' AND submission_no=:round AND deleted_at IS NULL",
                Map.of("id", id, "round", round, "status", approve ? "APPROVED" : "REJECTED"));
    }
    private static MapSqlParameterSource params(ResourceDraftRequest r, StoredResourceFile file) {
        return new MapSqlParameterSource().addValue("title", r.title()).addValue("description", r.description())
                .addValue("course", r.courseId()).addValue("category", r.categoryId()).addValue("key", file.key())
                .addValue("original", file.originalName()).addValue("mime", file.mimeType()).addValue("bytes", file.sizeBytes());
    }
    static TeachingResource map(ResultSet rs) throws SQLException {
        return new TeachingResource(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                rs.getLong("course_id"), rs.getString("course_name"), rs.getString("course_status"),
                rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_status"),
                rs.getLong("created_by"), rs.getString("status"), rs.getString("file_storage_key"),
                rs.getString("file_original_name"), rs.getString("file_mime_type"), rs.getLong("file_size_bytes"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("teacher_name"), rs.getLong("submission_no"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toLocalDateTime());
    }
}
