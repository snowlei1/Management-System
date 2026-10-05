package cn.edu.jxnu.civicsresources.resource;

import java.time.LocalDateTime;
import java.util.List;

// Public-use DTO: no storage key, audit opinions, submission rounds or private workflow fields.
public record PublishedResourceView(long id, String title, String description,
        long courseId, String courseName, String courseStatus,
        long categoryId, String categoryName, String categoryStatus, String teacherName,
        String fileOriginalName, String fileMimeType, long fileSizeBytes,
        LocalDateTime publishedAt, List<ResourceElementView> elements, boolean favorite) {
    static PublishedResourceView from(PublishedResourceRepository.Row row, List<ResourceElementView> elements) {
        var r = row.resource();
        return new PublishedResourceView(r.id(), r.title(), r.description(), r.courseId(), r.courseName(),
                r.courseStatus(), r.categoryId(), r.categoryName(), r.categoryStatus(), r.teacherName(),
                r.originalName(), r.mimeType(), r.sizeBytes(), r.publishedAt(), elements, row.favorite());
    }
}
