package cn.edu.jxnu.civicsresources.resource;
import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
public class ResourceSupplementController {
 private final ResourceSupplementService service;public ResourceSupplementController(ResourceSupplementService s){service=s;}
 @GetMapping("/api/teacher/resources/{id}/metadata") @PreAuthorize("hasRole('TEACHER')") public ApiResponse<ResourceSupplementService.Metadata> own(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){return ApiResponse.success(service.metadata(u,id,"teacher"));}
 @GetMapping("/api/resources/{id}/metadata") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')") public ApiResponse<ResourceSupplementService.Metadata> publicMetadata(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){return ApiResponse.success(service.metadata(u,id,"public"));}
 @GetMapping("/api/admin/resource-reviews/{id}/metadata") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<ResourceSupplementService.Metadata> review(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){return ApiResponse.success(service.metadata(u,id,"review"));}
 @PutMapping("/api/teacher/resources/{id}/metadata") @PreAuthorize("hasRole('TEACHER')") public ApiResponse<Void> save(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id,@RequestBody ResourceSupplementService.MetadataRequest r){service.save(u,id,r);return ApiResponse.success(null);}
 @PutMapping(value="/api/teacher/resources/{id}/cover",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('TEACHER')") public ApiResponse<Void> saveCover(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id,@RequestPart MultipartFile file){service.cover(u,id,file);return ApiResponse.success(null);}
 @DeleteMapping("/api/teacher/resources/{id}/cover") @PreAuthorize("hasRole('TEACHER')") public ApiResponse<Void> removeCover(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){service.removeCover(u,id);return ApiResponse.success(null);}
 @GetMapping("/api/teacher/resources/{id}/cover") @PreAuthorize("hasRole('TEACHER')") public ResponseEntity<byte[]> ownCover(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){return image(service.cover(u,id,"teacher"));}
 @GetMapping("/api/resources/{id}/cover") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')") public ResponseEntity<byte[]> cover(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){return image(service.cover(u,id,"public"));}
 @GetMapping("/api/admin/resource-reviews/{id}/cover") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<byte[]> reviewCover(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){return image(service.cover(u,id,"review"));}
 private ResponseEntity<byte[]> image(byte[] bytes){return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(bytes);}
}
