package cn.edu.jxnu.civicsresources.user;

import java.time.LocalDateTime;

public record AppUser(
        long id, String username, String passwordHash, String displayName,
        String role, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
