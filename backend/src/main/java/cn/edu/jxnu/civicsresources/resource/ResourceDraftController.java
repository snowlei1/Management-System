package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/teacher/resources")
@PreAuthorize("hasRole('TEACHER')")
public class ResourceDraftController {
    private final ResourceDraftService service;
    private final ObjectMapper mapper;
    private final ResourceStorageProperties storage;
    public ResourceDraftController(ResourceDraftService service, ObjectMapper mapper, ResourceStorageProperties storage) {
        this.service = service; this.mapper = mapper; this.storage = storage;
    }

    @GetMapping
    public ApiResponse<ResourceDraftPage> list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.list(user, keyword, courseId, categoryId, status, page, size));
    }
    @GetMapping("/upload-policy")
    public ApiResponse<Map<String, Object>> policy() {
        return ApiResponse.success(Map.of("maxSizeBytes", storage.maxSize().toBytes(), "allowedExtensions", storage.allowedExtensions()));
    }
    @GetMapping("/{id}")
    public ApiResponse<ResourceDraftView> detail(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return ApiResponse.success(service.detail(user, id));
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ResourceDraftView> create(@AuthenticationPrincipal UserPrincipal user,
            @RequestPart String metadata, @RequestPart MultipartFile file) {
        return ApiResponse.success(service.create(user, parse(metadata), file));
    }
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResourceDraftView> update(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id,
            @RequestPart String metadata, @RequestPart(required = false) MultipartFile file) {
        return ApiResponse.success(service.update(user, id, parse(metadata), file));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        service.delete(user, id);
        return ApiResponse.success(null);
    }
    @PostMapping("/{id}/submit")
    public ApiResponse<ResourceDraftView> submit(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return ApiResponse.success(service.submit(user, id));
    }
    private ResourceDraftRequest parse(String metadata) {
        try {
            return mapper.readerFor(ResourceDraftRequest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(metadata);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "资源参数不正确，仅接受标题、简介、课程、分类和元素ID");
        }
    }
}
