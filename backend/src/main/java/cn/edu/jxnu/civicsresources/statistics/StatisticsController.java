package cn.edu.jxnu.civicsresources.statistics;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/statistics")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {
    private final StatisticsService service;
    public StatisticsController(StatisticsService service) { this.service = service; }
    @GetMapping("/overview")
    public ApiResponse<StatisticsOverview> overview(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(service.overview(user));
    }
}
