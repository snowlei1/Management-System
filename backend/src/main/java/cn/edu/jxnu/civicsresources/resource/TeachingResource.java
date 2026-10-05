package cn.edu.jxnu.civicsresources.resource;

import java.time.LocalDateTime;

// Internal persistence object: do not expose storageKey in an API response.
public record TeachingResource(long id, String title, String description, long courseId,
        String courseName, String courseStatus, long categoryId, String categoryName,
        String categoryStatus, long createdBy, String status, String storageKey,
        String originalName, String mimeType, long sizeBytes, LocalDateTime createdAt,
        LocalDateTime updatedAt, String teacherName, long submissionNo, LocalDateTime publishedAt) { }
