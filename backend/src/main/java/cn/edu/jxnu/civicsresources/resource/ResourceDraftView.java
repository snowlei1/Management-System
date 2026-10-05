package cn.edu.jxnu.civicsresources.resource;

import java.time.LocalDateTime;
import java.util.List;

public record ResourceDraftView(long id, String title, String description, long courseId,
        String courseName, String courseStatus, long categoryId, String categoryName,
        String categoryStatus, long createdBy, String status, List<ResourceElementView> elements,
        String fileOriginalName, String fileMimeType, long fileSizeBytes,
        LocalDateTime createdAt, LocalDateTime updatedAt, String teacherName, long submissionNo,
        LocalDateTime pendingSubmittedAt, LocalDateTime publishedAt, List<AuditRecordView> auditRecords) {
    static ResourceDraftView from(TeachingResource r, List<ResourceElementView> elements) {
        return new ResourceDraftView(r.id(), r.title(), r.description(), r.courseId(), r.courseName(),
                r.courseStatus(), r.categoryId(), r.categoryName(), r.categoryStatus(), r.createdBy(),
                r.status(), elements, r.originalName(), r.mimeType(), r.sizeBytes(), r.createdAt(), r.updatedAt(),
                r.teacherName(), r.submissionNo(), "PENDING".equals(r.status()) ? r.updatedAt() : null,
                r.publishedAt(), List.of());
    }
    static ResourceDraftView detail(TeachingResource r, List<ResourceElementView> elements, List<AuditRecordView> history) {
        var v = from(r, elements);
        return new ResourceDraftView(v.id(), v.title(), v.description(), v.courseId(), v.courseName(), v.courseStatus(),
                v.categoryId(), v.categoryName(), v.categoryStatus(), v.createdBy(), v.status(), v.elements(),
                v.fileOriginalName(), v.fileMimeType(), v.fileSizeBytes(), v.createdAt(), v.updatedAt(),
                v.teacherName(), v.submissionNo(), v.pendingSubmittedAt(), v.publishedAt(), history);
    }
}
