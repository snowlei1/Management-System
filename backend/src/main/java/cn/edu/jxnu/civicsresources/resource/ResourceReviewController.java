package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/resource-reviews")
@PreAuthorize("hasRole('ADMIN')")
public class ResourceReviewController {
    private final ResourceReviewService service;
    public ResourceReviewController(ResourceReviewService service) { this.service = service; }
    @GetMapping
    public ApiResponse<ResourceDraftPage> list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.list(user, keyword, courseId, categoryId, teacherId, status, page, size));
    }
    @GetMapping("/{id}")
    public ApiResponse<ResourceDraftView> detail(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return ApiResponse.success(service.detail(user, id));
    }
    @GetMapping("/{id}/attachment")
    public ResponseEntity<byte[]> attachment(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean download) {
        var a = service.attachment(user, id);
        boolean inline = !download && Set.of("application/pdf", "image/png", "image/jpeg").contains(a.mimeType());
        String disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(a.originalName(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.mimeType())).contentLength(a.bytes().length)
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(a.bytes());
    }
    @PostMapping("/{id}/approve")
    public ApiResponse<ResourceDraftView> approve(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id,
            @Valid @RequestBody ApproveRequest request) {
        return ApiResponse.success(service.approve(user, id, request.submissionNo()));
    }
    @PostMapping("/{id}/reject")
    public ApiResponse<ResourceDraftView> reject(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id,
            @Valid @RequestBody RejectRequest request) {
        return ApiResponse.success(service.reject(user, id, request.submissionNo(), request.reason()));
    }
    public record ApproveRequest(@NotNull(message="请提供审核轮次") @Min(value=1,message="审核轮次须大于0") Long submissionNo) { }
    public record RejectRequest(@NotNull(message="请提供审核轮次") @Min(value=1,message="审核轮次须大于0") Long submissionNo, String reason) { }
}
