package cn.edu.jxnu.civicsresources.resource;

import java.time.LocalDateTime;
import java.util.List;

public record ResourceDraftView(long id, String title, String description, long courseId,
        String courseName, String courseStatus, long categoryId, String categoryName,
        String categoryStatus, long createdBy, String status, List<ResourceElementView> elements,
        String fileOriginalName, String fileMimeType, long fileSizeBytes,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    static ResourceDraftView from(TeachingResource r, List<ResourceElementView> elements) {
        return new ResourceDraftView(r.id(), r.title(), r.description(), r.courseId(), r.courseName(),
                r.courseStatus(), r.categoryId(), r.categoryName(), r.categoryStatus(), r.createdBy(),
                r.status(), elements, r.originalName(), r.mimeType(), r.sizeBytes(), r.createdAt(), r.updatedAt());
    }
}
