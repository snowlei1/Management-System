package cn.edu.jxnu.civicsresources.basedata;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/{type:courses|ideological-elements|resource-categories}")
@PreAuthorize("hasRole('ADMIN')")
public class BaseDataController {
    private final BaseDataService service;

    public BaseDataController(BaseDataService service) { this.service = service; }

    @GetMapping
    public ApiResponse<BaseDataPage> list(@PathVariable String type,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.list(BaseDataType.fromPath(type), keyword, status, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BaseDataView> create(@PathVariable String type, @Valid @RequestBody BaseDataRequest request) {
        return ApiResponse.success(service.create(BaseDataType.fromPath(type), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BaseDataView> update(@PathVariable String type, @PathVariable long id,
            @Valid @RequestBody BaseDataRequest request) {
        return ApiResponse.success(service.update(BaseDataType.fromPath(type), id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<BaseDataView> setStatus(@PathVariable String type, @PathVariable long id,
            @Valid @RequestBody BaseDataStatusRequest request) {
        return ApiResponse.success(service.setStatus(BaseDataType.fromPath(type), id, request));
    }
}
