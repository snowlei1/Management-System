package cn.edu.jxnu.civicsresources.basedata;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

public record BaseDataView(long id,
        @JsonInclude(JsonInclude.Include.NON_NULL) String courseCode,
        String name, String description, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
