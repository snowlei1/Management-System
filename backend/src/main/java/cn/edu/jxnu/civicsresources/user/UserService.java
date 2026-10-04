package cn.edu.jxnu.civicsresources.user;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final Set<String> ROLES = Set.of("ADMIN", "TEACHER", "STUDENT");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserView current(long id) {
        AppUser user = requireUser(id);
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态已失效");
        }
        return UserView.from(user);
    }

    public UserPage list(String keyword, String role, String status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw badRequest("分页参数不正确");
        }
        if (keyword != null && keyword.length() > 80) {
            throw badRequest("搜索词不能超过80个字符");
        }
        if (role != null && !role.isBlank() && !ROLES.contains(role)) {
            throw badRequest("角色筛选值不正确");
        }
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw badRequest("状态筛选值不正确");
        }
        return repository.findPage(keyword, role, status, page, size);
    }

    @Transactional
    public UserView create(CreateUserRequest request) {
        String username = validateUsername(request.username());
        String displayName = validateDisplayName(request.displayName());
        String role = validateManagedRole(request.role());
        validatePassword(request.password());
        if (repository.findByUsername(username).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONFLICT", "账号已存在");
        }
        long id = repository.insert(username, passwordEncoder.encode(request.password()), displayName, role);
        return UserView.from(requireUser(id));
    }

    @Transactional
    public UserView update(long id, UpdateUserRequest request, long currentAdminId) {
        AppUser existing = requireUser(id);
        String username = validateUsername(request.username());
        String displayName = validateDisplayName(request.displayName());
        String role = request.role().trim().toUpperCase();
        if ("ADMIN".equals(existing.role())) {
            if (id != currentAdminId || !"ADMIN".equals(role)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "不能更改管理员角色");
            }
        } else {
            role = validateManagedRole(role);
            if (!existing.role().equals(role) && repository.hasAuthoredResources(id)) {
                throw new BusinessException(HttpStatus.CONFLICT, "CONFLICT", "该用户已有创建的资源，不能更改角色");
            }
        }
        repository.findByUsername(username).ifPresent(other -> {
            if (other.id() != id) {
                throw new BusinessException(HttpStatus.CONFLICT, "CONFLICT", "账号已存在");
            }
        });
        repository.update(id, username, displayName, role);
        return UserView.from(requireUser(id));
    }

    @Transactional
    public UserView setStatus(long id, UpdateUserStatusRequest request) {
        AppUser existing = requireUser(id);
        String status = request.status().trim().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw badRequest("用户状态不正确");
        }
        if ("ADMIN".equals(existing.role())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "不能停用或修改管理员账号状态");
        }
        repository.updateStatus(id, status);
        return UserView.from(requireUser(id));
    }

    private AppUser requireUser(long id) {
        return repository.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "用户不存在"));
    }

    private static String validateUsername(String value) {
        String username = value.trim();
        if (!username.matches("[A-Za-z0-9._-]{3,64}")) {
            throw badRequest("账号须为3—64位字母、数字、点、下划线或连字符");
        }
        return username;
    }

    private static String validateDisplayName(String value) {
        String name = value.trim();
        if (name.isEmpty() || name.length() > 80) {
            throw badRequest("姓名长度须为1—80个字符");
        }
        return name;
    }

    private static String validateManagedRole(String value) {
        String role = value.trim().toUpperCase();
        if (!"TEACHER".equals(role) && !"STUDENT".equals(role)) {
            throw badRequest("只能创建或指定教师、学生角色");
        }
        return role;
    }

    private static void validatePassword(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) {
            throw badRequest("密码长度须为8—72字节");
        }
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
