package cn.edu.jxnu.civicsresources.auth;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import cn.edu.jxnu.civicsresources.user.UserService;
import cn.edu.jxnu.civicsresources.user.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final UserService users;

    public AuthController(AuthenticationManager authenticationManager,
            SecurityContextRepository contexts, UserService users) {
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
        this.users = users;
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        return ApiResponse.success(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    @PostMapping("/login")
    public ApiResponse<UserView> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username().trim(), request.password()));
        if (servletRequest.getSession(false) != null) {
            servletRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, servletRequest, servletResponse);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ApiResponse.success(users.current(principal.id()));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(users.current(principal.id()));
    }
}
