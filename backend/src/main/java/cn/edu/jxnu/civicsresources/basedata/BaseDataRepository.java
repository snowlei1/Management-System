package cn.edu.jxnu.civicsresources.basedata;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BaseDataRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public BaseDataRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<BaseDataView> findById(BaseDataType type, long id) {
        return jdbc.query(select(type) + " WHERE id=:id", Map.of("id", id),
                (rs, row) -> map(type, rs)).stream().findFirst();
    }

    public boolean uniqueValueExists(BaseDataType type, String value, long excludedId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + type.table()
                + " WHERE " + type.uniqueColumn() + "=:value AND id<>:excludedId",
                Map.of("value", value, "excludedId", excludedId), Long.class);
        return count != null && count > 0;
    }

    public BaseDataPage findPage(BaseDataType type, String keyword, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (keyword != null && !keyword.isBlank()) {
            where.append(type.isCourse()
                    ? " AND (name LIKE :keyword ESCAPE '!' OR course_code LIKE :keyword ESCAPE '!')"
                    : " AND name LIKE :keyword ESCAPE '!'");
            params.addValue("keyword", "%" + escapeLike(keyword.trim()) + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status=:status");
            params.addValue("status", status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + type.table() + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", ((long) page - 1) * size);
        List<BaseDataView> items = jdbc.query(select(type) + where + " ORDER BY id DESC LIMIT :limit OFFSET :offset",
                params, (rs, row) -> map(type, rs));
        return new BaseDataPage(items, total == null ? 0 : total, page, size);
    }

    public List<BaseDataOption> findActiveOptions(BaseDataType type) {
        String columns = type.isCourse() ? "id,course_code,name" : "id,name";
        return jdbc.query("SELECT " + columns + " FROM " + type.table()
                + " WHERE status='ACTIVE' ORDER BY name,id", Map.of(),
                (rs, row) -> new BaseDataOption(rs.getLong("id"),
                        type.isCourse() ? rs.getString("course_code") : null, rs.getString("name")));
    }

    public long insert(BaseDataType type, BaseDataRequest request) {
        String columns = type.isCourse() ? "course_code,name,description" : "name,description";
        String values = type.isCourse() ? ":courseCode,:name,:description" : ":name,:description";
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO " + type.table() + " (" + columns + ") VALUES (" + values + ")",
                parameters(request), holder, new String[]{"id"});
        return holder.getKey().longValue();
    }

    public void update(BaseDataType type, long id, BaseDataRequest request) {
        String fields = type.isCourse() ? "course_code=:courseCode,name=:name,description=:description"
                : "name=:name,description=:description";
        jdbc.update("UPDATE " + type.table() + " SET " + fields + " WHERE id=:id",
                parameters(request).addValue("id", id));
    }

    public void updateStatus(BaseDataType type, long id, String status) {
        jdbc.update("UPDATE " + type.table() + " SET status=:status WHERE id=:id", Map.of("id", id, "status", status));
    }

    private static MapSqlParameterSource parameters(BaseDataRequest request) {
        return new MapSqlParameterSource().addValue("courseCode", request.courseCode())
                .addValue("name", request.name()).addValue("description", request.description());
    }

    private static String select(BaseDataType type) {
        return "SELECT id," + (type.isCourse() ? "course_code," : "")
                + "name,description,status,created_at,updated_at FROM " + type.table();
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static BaseDataView map(BaseDataType type, ResultSet rs) throws SQLException {
        return new BaseDataView(rs.getLong("id"), type.isCourse() ? rs.getString("course_code") : null,
                rs.getString("name"), rs.getString("description"), rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
