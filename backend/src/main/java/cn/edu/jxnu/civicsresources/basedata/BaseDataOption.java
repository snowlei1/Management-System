package cn.edu.jxnu.civicsresources.basedata;

import com.fasterxml.jackson.annotation.JsonInclude;

public record BaseDataOption(long id, @JsonInclude(JsonInclude.Include.NON_NULL) String courseCode, String name) {
}
