package cn.edu.jxnu.civicsresources.security;

import cn.edu.jxnu.civicsresources.user.AppUser;
import cn.edu.jxnu.civicsresources.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveUserFilter extends OncePerRequestFilter {
    private final UserRepository repository;
    private final SecurityResponseWriter writer;

    public ActiveUserFilter(UserRepository repository, SecurityResponseWriter writer) {
        this.repository = repository;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            Optional<AppUser> current = repository.findById(principal.id());
            if (current.isEmpty() || !"ACTIVE".equals(current.get().status())
                    || !current.get().role().equals(principal.role())
                    || !current.get().username().equals(principal.username())) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                writer.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "登录状态已失效");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
