package cn.edu.jxnu.civicsresources.security;

import cn.edu.jxnu.civicsresources.user.AppUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(long id, String username, String password, String role, String status)
        implements UserDetails {
    public static UserPrincipal from(AppUser user) {
        return new UserPrincipal(user.id(), user.username(), user.passwordHash(), user.role(), user.status());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }
}
