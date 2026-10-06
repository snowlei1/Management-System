package cn.edu.jxnu.civicsresources.resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Read-only presentation models. No file keys, credentials or fabricated workflow events. */
public final class ResourceReadModels {
    private ResourceReadModels() { }
    public record NavigationItem(long id, String code, String name, String description, String status, long resourceCount) { }
    public record NavigationDetail(NavigationItem item, List<NavigationItem> related) { }
    public record Usage(long browseEvents, long downloadRequests, long currentFavorites) {
        static Usage zero() { return new Usage(0, 0, 0); }
    }
    public record Presentation(Usage usage, List<PublishedResourceView> sameCourse, List<PublishedResourceView> sameElements) { }
    public record HistoryItem(long id, long resourceId, String title, long courseId, String courseName,
            String fileOriginalName, String fileMimeType, LocalDateTime accessedAt) { }
    public record HistoryPage(List<HistoryItem> items, long total, int page, int size) { }
    public record ResourceRow(ResourceDraftView resource, Usage usage, AuditRecordView latestAudit) { }
    public record ResourceTable(List<ResourceRow> items, long total, int page, int size) { }
    public record TeacherDashboard(Map<String, Long> counts, List<ResourceRow> recent,
            List<ResourceRow> rejected, List<ResourceRow> published) { }
}
