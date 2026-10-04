package cn.edu.jxnu.civicsresources.security;

import cn.edu.jxnu.civicsresources.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository repository;

    public DatabaseUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByUsername(username).map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
    }
}
