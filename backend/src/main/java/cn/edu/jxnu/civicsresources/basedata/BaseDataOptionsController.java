package cn.edu.jxnu.civicsresources.basedata;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
public class BaseDataOptionsController {
    private final BaseDataService service;

    public BaseDataOptionsController(BaseDataService service) { this.service = service; }

    @GetMapping("/api/options/{type:courses|ideological-elements|resource-categories}")
    public ApiResponse<List<BaseDataOption>> options(@PathVariable String type) {
        return ApiResponse.success(service.options(BaseDataType.fromPath(type)));
    }
}
