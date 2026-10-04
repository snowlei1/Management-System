package cn.edu.jxnu.civicsresources.user;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<UserPage> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.list(keyword, role, status, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@PathVariable long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(service.update(id, request, principal.id()));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserView> setStatus(@PathVariable long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(service.setStatus(id, request));
    }
}
