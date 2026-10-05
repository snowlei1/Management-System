package cn.edu.jxnu.civicsresources.statistics;

import java.util.List;

/** Counts are descriptive aggregates, not unique visitors or learning outcomes. */
public record StatisticsOverview(Users users, BaseCount courses, BaseCount elements,
        BaseCount categories, Resources resources, Usage usage, List<Distribution> courseDistribution,
        List<Distribution> categoryDistribution, List<Distribution> elementDistribution) {
    public record Users(long total, long admin, long teacher, long student, long active, long disabled) { }
    public record BaseCount(long total, long active) { }
    public record Resources(long total, long draft, long pending, long rejected, long approved, long deleted) { }
    public record Usage(long browseEvents, long downloadRequests, long currentFavorites) { }
    public record Distribution(long id, String name, String status, long resourceCount) { }
}
