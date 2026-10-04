package cn.edu.jxnu.civicsresources.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository repository;
    @Mock PasswordEncoder encoder;
    @InjectMocks UserService service;

    @Test
    void createsTeacherWithHashedPassword() {
        when(repository.findByUsername("new_teacher")).thenReturn(Optional.empty());
        when(encoder.encode("SafePass#2026")).thenReturn("bcrypt-hash");
        when(repository.insert("new_teacher", "bcrypt-hash", "新教师", "TEACHER")).thenReturn(42L);
        when(repository.findById(42L)).thenReturn(Optional.of(user(42L, "TEACHER", "ACTIVE")));

        UserView created = service.create(new CreateUserRequest("new_teacher", "SafePass#2026", "新教师", "TEACHER"));

        assertEquals(42L, created.id());
        assertEquals("TEACHER", created.role());
        verify(repository).insert("new_teacher", "bcrypt-hash", "新教师", "TEACHER");
    }

    @Test
    void cannotCreateAnotherAdministratorFromUserManagement() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.create(new CreateUserRequest("new_admin", "SafePass#2026", "管理员", "ADMIN")));
        assertEquals("BAD_REQUEST", exception.code());
        verify(repository, never()).insert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void administratorCannotBeDisabled() {
        when(repository.findById(1L)).thenReturn(Optional.of(user(1L, "ADMIN", "ACTIVE")));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.setStatus(1L, new UpdateUserStatusRequest("DISABLED")));

        assertEquals("FORBIDDEN", exception.code());
        verify(repository, never()).updateStatus(1L, "DISABLED");
    }

    private static AppUser user(long id, String role, String status) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 4, 12, 0);
        return new AppUser(id, "new_teacher", "bcrypt-hash", "新教师", role, status, now, now);
    }
}
