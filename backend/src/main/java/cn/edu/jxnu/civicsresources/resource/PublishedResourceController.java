package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
public class PublishedResourceController {
    private final PublishedResourceService service;
    public PublishedResourceController(PublishedResourceService service) { this.service = service; }
    @GetMapping("/api/resources")
    public ApiResponse<PublishedResourcePage> list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) Long courseId,
            @RequestParam(required=false) Long categoryId, @RequestParam(required=false) Long elementId,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.list(user, keyword, courseId, categoryId, elementId, false, page, size));
    }
    @GetMapping("/api/favorites")
    public ApiResponse<PublishedResourcePage> favorites(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.list(user, null, null, null, null, true, page, size));
    }
    @GetMapping("/api/resources/{id}")
    public ApiResponse<PublishedResourceView> detail(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.detail(user, id, !"HEAD".equals(request.getMethod())));
    }
    @GetMapping("/api/resources/{id}/preview")
    public ResponseEntity<byte[]> preview(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return attachment(service.attachment(user, id, false), false);
    }
    @GetMapping("/api/resources/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id, HttpServletRequest request) {
        return attachment(service.attachment(user, id, true, !"HEAD".equals(request.getMethod())), true);
    }
    @PostMapping("/api/resources/{id}/favorite")
    public ApiResponse<PublishedResourceService.FavoriteState> favorite(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return ApiResponse.success(service.favorite(user, id, true));
    }
    @DeleteMapping("/api/resources/{id}/favorite")
    public ApiResponse<PublishedResourceService.FavoriteState> unfavorite(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) {
        return ApiResponse.success(service.favorite(user, id, false));
    }
    private static ResponseEntity<byte[]> attachment(PublishedResourceService.Attachment a, boolean download) {
        String disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(a.originalName(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.mimeType())).contentLength(a.bytes().length)
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "sandbox; default-src 'none'").body(a.bytes());
    }
}
