package cn.edu.jxnu.civicsresources.basedata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BaseDataRequest(
        @Size(max = 40, message = "课程编号不能超过40个字符") String courseCode,
        @NotBlank(message = "名称不能为空") @Size(max = 120, message = "名称不能超过120个字符") String name,
        @Size(max = 1000, message = "说明不能超过1000个字符") String description) {
}
