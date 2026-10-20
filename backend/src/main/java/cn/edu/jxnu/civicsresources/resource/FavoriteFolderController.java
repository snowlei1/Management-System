package cn.edu.jxnu.civicsresources.resource;
import cn.edu.jxnu.civicsresources.common.ApiResponse;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
public class FavoriteFolderController {
 private final FavoriteFolderService service;
 public FavoriteFolderController(FavoriteFolderService service){this.service=service;}
 @GetMapping("/api/favorite-folders") public ApiResponse<List<FavoriteFolderService.Folder>> list(@AuthenticationPrincipal UserPrincipal u){return ApiResponse.success(service.list(u));}
 @PostMapping("/api/favorite-folders") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public ApiResponse<FavoriteFolderService.Folder> create(@AuthenticationPrincipal UserPrincipal u,@RequestBody FavoriteFolderService.NameRequest r){return ApiResponse.success(service.create(u,r));}
 @PutMapping("/api/favorite-folders/{id}") public ApiResponse<FavoriteFolderService.Folder> rename(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id,@RequestBody FavoriteFolderService.NameRequest r){return ApiResponse.success(service.rename(u,id,r));}
 @DeleteMapping("/api/favorite-folders/{id}") public ApiResponse<Void> remove(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id){service.remove(u,id);return ApiResponse.success(null);}
 @PutMapping("/api/favorites/{id}/folder") public ApiResponse<Void> move(@AuthenticationPrincipal UserPrincipal u,@PathVariable long id,@RequestBody FavoriteFolderService.MoveRequest r){service.move(u,id,r);return ApiResponse.success(null);}
}
