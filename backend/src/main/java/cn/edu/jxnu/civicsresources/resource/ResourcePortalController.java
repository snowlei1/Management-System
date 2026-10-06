package cn.edu.jxnu.civicsresources.resource;

import static cn.edu.jxnu.civicsresources.resource.ResourceReadModels.*;
import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ResourcePortalController {
    private final ResourcePortalService service;
    public ResourcePortalController(ResourcePortalService service){this.service=service;}
    @GetMapping("/api/portal/courses") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ApiResponse<List<NavigationItem>> courses(@AuthenticationPrincipal UserPrincipal user){return ApiResponse.success(service.navigation(user,false));}
    @GetMapping("/api/portal/courses/{id}") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ApiResponse<NavigationDetail> course(@AuthenticationPrincipal UserPrincipal user,@PathVariable long id){return ApiResponse.success(service.navigationDetail(user,false,id));}
    @GetMapping("/api/portal/ideological-topics") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ApiResponse<List<NavigationItem>> topics(@AuthenticationPrincipal UserPrincipal user){return ApiResponse.success(service.navigation(user,true));}
    @GetMapping("/api/portal/ideological-topics/{id}") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ApiResponse<NavigationDetail> topic(@AuthenticationPrincipal UserPrincipal user,@PathVariable long id){return ApiResponse.success(service.navigationDetail(user,true,id));}
    @GetMapping("/api/portal/resources/{id}") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ApiResponse<Presentation> presentation(@AuthenticationPrincipal UserPrincipal user,@PathVariable long id){return ApiResponse.success(service.presentation(user,id));}
    @GetMapping("/api/history/{kind}") @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    public ApiResponse<HistoryPage> history(@AuthenticationPrincipal UserPrincipal user,@PathVariable String kind,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int size){return ApiResponse.success(service.history(user,kind,page,size));}
    @GetMapping("/api/teacher/resource-dashboard") @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<TeacherDashboard> dashboard(@AuthenticationPrincipal UserPrincipal user){return ApiResponse.success(service.dashboard(user));}
    @GetMapping("/api/teacher/resource-presentations") @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<ResourceRow>> ownPresentations(@AuthenticationPrincipal UserPrincipal user,@RequestParam List<Long> ids){return ApiResponse.success(service.ownPresentations(user,ids));}
    @GetMapping("/api/admin/published-resources") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ResourceTable> ledger(@AuthenticationPrincipal UserPrincipal user,@RequestParam(required=false)String keyword,@RequestParam(required=false)Long courseId,@RequestParam(required=false)Long categoryId,@RequestParam(required=false)Long teacherId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int size){return ApiResponse.success(service.ledger(user,keyword,courseId,categoryId,teacherId,page,size));}
    @GetMapping("/api/admin/review-overview") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ResourceRow>> pendingRecent(@AuthenticationPrincipal UserPrincipal user){return ApiResponse.success(service.pendingRecent(user));}
}
