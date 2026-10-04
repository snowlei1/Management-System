package cn.edu.jxnu.civicsresources.user;

import java.time.LocalDateTime;

public record UserView(
        long id, String username, String displayName, String role, String status,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static UserView from(AppUser user) {
        return new UserView(user.id(), user.username(), user.displayName(), user.role(),
                user.status(), user.createdAt(), user.updatedAt());
    }
}
