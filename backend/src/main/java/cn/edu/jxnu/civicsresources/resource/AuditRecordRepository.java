package cn.edu.jxnu.civicsresources.resource;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRecordRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AuditRecordRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AuditRecordView> history(long resource) {
        return jdbc.query("""
                SELECT a.*,u.display_name AS reviewer_name FROM audit_record a
                JOIN app_user u ON u.id=a.reviewer_id WHERE a.resource_id=:id ORDER BY a.submission_no,a.id
                """, Map.of("id", resource), (rs, row) -> new AuditRecordView(rs.getLong("id"), rs.getLong("submission_no"),
                rs.getLong("reviewer_id"), rs.getString("reviewer_name"), rs.getString("from_status"),
                rs.getString("decision"), rs.getString("reason"), rs.getTimestamp("audited_at").toLocalDateTime()));
    }
    public void insert(long resource, long round, long reviewer, boolean approve, String reason) {
        jdbc.update("""
                INSERT INTO audit_record(resource_id,submission_no,reviewer_id,from_status,decision,reason)
                VALUES (:resource,:round,:reviewer,'PENDING',:decision,:reason)
                """, new MapSqlParameterSource("resource", resource).addValue("round", round).addValue("reviewer", reviewer)
                .addValue("decision", approve ? "APPROVE" : "REJECT").addValue("reason", reason));
    }
}
