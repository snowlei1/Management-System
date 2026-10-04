package cn.edu.jxnu.civicsresources.system;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final JdbcTemplate jdbcTemplate;

    public SystemController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (result == null || result != 1) {
            throw new IllegalStateException("数据库连接检查未通过");
        }
        return ApiResponse.success(Map.of("service", "UP", "database", "UP"));
    }
}
