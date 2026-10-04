package cn.edu.jxnu.civicsresources.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "账号不能为空") @Size(max = 64, message = "账号不能超过64个字符") String username,
        @NotBlank(message = "姓名不能为空") @Size(max = 80, message = "姓名不能超过80个字符") String displayName,
        @NotBlank(message = "角色不能为空") String role) {
}
