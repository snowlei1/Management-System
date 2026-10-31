package cn.edu.jxnu.civicsresources.resource;
import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class ResourceGovernanceController {
 private final ResourceGovernanceService service;public ResourceGovernanceController(ResourceGovernanceService s){service=s;}
 @GetMapping("/api/admin/published-resources/{id}/publication") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<ResourceGovernanceService.Publication> publication(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){return ApiResponse.success(service.publication(u,id));}
 @PostMapping("/api/admin/published-resources/{id}/withdraw") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<ResourceGovernanceService.Publication> withdraw(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id,@RequestBody ResourceGovernanceService.PublicationRequest r){return ApiResponse.success(service.change(u,id,true,r));}
 @PostMapping("/api/admin/published-resources/{id}/restore") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<ResourceGovernanceService.Publication> restore(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id,@RequestBody ResourceGovernanceService.PublicationRequest r){return ApiResponse.success(service.change(u,id,false,r));}
 @PostMapping("/api/resources/{id}/feedback") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')") public ApiResponse<Void> report(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id,@RequestBody ResourceGovernanceService.FeedbackRequest r){service.report(u,id,r);return ApiResponse.success(null);}
 @GetMapping("/api/resource-feedback/mine") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')") public ApiResponse<ResourceGovernanceService.FeedbackPage> mine(@AuthenticationPrincipal UserPrincipal u,@RequestParam(required=false)String status,@RequestParam(required=false)Long resourceId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int size){return ApiResponse.success(service.feedback(u,false,status,resourceId,page,size));}
 @GetMapping("/api/admin/resource-feedback") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<ResourceGovernanceService.FeedbackPage> feedback(@AuthenticationPrincipal UserPrincipal u,@RequestParam(required=false)String status,@RequestParam(required=false)Long resourceId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int size){return ApiResponse.success(service.feedback(u,true,status,resourceId,page,size));}
 @PostMapping("/api/admin/resource-feedback/{id}/handle") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<Void> handle(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id,@RequestBody ResourceGovernanceService.HandleRequest r){service.handle(u,id,r);return ApiResponse.success(null);}
}
