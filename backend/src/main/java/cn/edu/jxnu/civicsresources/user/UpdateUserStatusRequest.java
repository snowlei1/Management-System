package cn.edu.jxnu.civicsresources.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserStatusRequest(@NotBlank(message = "状态不能为空") String status) {
}
