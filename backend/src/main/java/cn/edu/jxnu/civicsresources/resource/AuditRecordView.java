package cn.edu.jxnu.civicsresources.resource;

import java.time.LocalDateTime;

public record AuditRecordView(long id, long submissionNo, long reviewerId, String reviewerName,
        String fromStatus, String decision, String reason, LocalDateTime auditedAt) { }
