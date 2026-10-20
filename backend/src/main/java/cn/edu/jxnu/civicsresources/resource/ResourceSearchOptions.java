package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Fixed allowlists: user input is never interpolated into SQL ordering. */
public record ResourceSearchOptions(String format, String sort, LocalDate from, LocalDate to) {
    public static ResourceSearchOptions defaults() { return new ResourceSearchOptions("ALL", "NEWEST", null, null); }
    public static ResourceSearchOptions parse(String format, String sort, String from, String to) {
        try {
            var result = new ResourceSearchOptions(format == null || format.isBlank() ? "ALL" : format,
                    sort == null || sort.isBlank() ? "NEWEST" : sort,
                    from == null || from.isBlank() ? null : LocalDate.parse(from),
                    to == null || to.isBlank() ? null : LocalDate.parse(to));
            if (!Set.of("ALL", "PDF", "IMAGE", "OFFICE").contains(result.format)
                    || !Set.of("NEWEST", "OLDEST", "TITLE").contains(result.sort)
                    || result.from != null && (result.from.getYear() < 1 || result.from.getYear() > 9998)
                    || result.to != null && (result.to.getYear() < 1 || result.to.getYear() > 9998)
                    || result.from != null && result.to != null && result.from.isAfter(result.to)) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "文件格式、排序或日期范围不正确");
        }
    }
    String orderBy() {
        return switch (sort) {
            case "OLDEST" -> "r.published_at ASC,r.id ASC";
            case "TITLE" -> "r.title ASC,r.id DESC";
            default -> "r.published_at DESC,r.id DESC";
        };
    }
}
