package cn.edu.jxnu.civicsresources.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private static final String SELECT_USER = """
            SELECT u.id, u.username, u.password_hash, u.display_name, r.code AS role_code,
                   u.status, u.created_at, u.updated_at
            FROM app_user u JOIN role r ON r.id = u.role_id
            """;
    private static final RowMapper<AppUser> MAPPER = UserRepository::mapUser;
    private final NamedParameterJdbcTemplate jdbc;

    public UserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByUsername(String username) {
        List<AppUser> users = jdbc.query(SELECT_USER + " WHERE u.username = :username",
                Map.of("username", username), MAPPER);
        return users.stream().findFirst();
    }

    public Optional<AppUser> findById(long id) {
        List<AppUser> users = jdbc.query(SELECT_USER + " WHERE u.id = :id",
                Map.of("id", id), MAPPER);
        return users.stream().findFirst();
    }

    public UserPage findPage(String keyword, String role, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.username LIKE :keyword OR u.display_name LIKE :keyword)");
            params.addValue("keyword", "%" + escapeLike(keyword.trim()) + "%");
        }
        if (role != null && !role.isBlank()) {
            where.append(" AND r.code = :role");
            params.addValue("role", role);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND u.status = :status");
            params.addValue("status", status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM app_user u JOIN role r ON r.id=u.role_id"
                + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) (page - 1) * size);
        List<UserView> items = jdbc.query(SELECT_USER + where + " ORDER BY u.id DESC LIMIT :limit OFFSET :offset",
                params, MAPPER).stream().map(UserView::from).toList();
        return new UserPage(items, total == null ? 0 : total, page, size);
    }

    public long insert(String username, String passwordHash, String displayName, String role) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", username).addValue("passwordHash", passwordHash)
                .addValue("displayName", displayName).addValue("role", role);
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO app_user (username, password_hash, display_name, role_id)
                VALUES (:username, :passwordHash, :displayName, (SELECT id FROM role WHERE code=:role))
                """, params, holder, new String[]{"id"});
        return holder.getKey().longValue();
    }

    public void update(long id, String username, String displayName, String role) {
        jdbc.update("""
                UPDATE app_user SET username=:username, display_name=:displayName,
                    role_id=(SELECT id FROM role WHERE code=:role)
                WHERE id=:id
                """, Map.of("id", id, "username", username, "displayName", displayName, "role", role));
    }

    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE app_user SET status=:status WHERE id=:id", Map.of("id", id, "status", status));
    }

    public boolean hasAuthoredResources(long id) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM teaching_resource WHERE created_by=:id",
                Map.of("id", id), Long.class);
        return count != null && count > 0;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static AppUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AppUser(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getString("role_code"), rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
