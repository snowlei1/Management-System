package cn.edu.jxnu.civicsresources.basedata;

import jakarta.validation.constraints.NotBlank;

public record BaseDataStatusRequest(@NotBlank(message = "状态不能为空") String status) {
}
