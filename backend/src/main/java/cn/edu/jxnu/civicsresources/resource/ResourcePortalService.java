package cn.edu.jxnu.civicsresources.resource;

import static cn.edu.jxnu.civicsresources.resource.ResourceReadModels.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ResourcePortalService {
    private final ResourcePortalRepository repository;
    public ResourcePortalService(ResourcePortalRepository repository) { this.repository=repository; }
    public List<NavigationItem> navigation(UserPrincipal user, boolean topics) { role(user,"TEACHER","STUDENT"); return repository.navigation(topics,null,null); }
    public NavigationDetail navigationDetail(UserPrincipal user, boolean topics, long id) {
        role(user,"TEACHER","STUDENT"); id(id);
        var item=repository.navigation(topics,null,null).stream().filter(n->n.id()==id).findFirst().orElseThrow(ResourcePortalService::notFound);
        return new NavigationDetail(item,repository.navigation(!topics,topics?null:id,topics?id:null));
    }
    public Presentation presentation(UserPrincipal user, long id) {
        role(user,"TEACHER","STUDENT");id(id); if(!repository.visible(id)) throw notFound();
        return new Presentation(repository.usage(List.of(id)).get(id),repository.related(id,user.id(),false),repository.related(id,user.id(),true));
    }
    public HistoryPage history(UserPrincipal user, String kind, int page, int size) {
        role(user,"TEACHER","STUDENT");page(page,size);
        if(!Set.of("browse","downloads").contains(kind)) throw bad("记录类型不正确");
        return repository.history(user.id(),"downloads".equals(kind),page,size);
    }
    public TeacherDashboard dashboard(UserPrincipal user) {
        role(user,"TEACHER");
        var recent=repository.ownRecent(user.id(),null);var rejected=repository.ownRecent(user.id(),"REJECTED");var published=repository.ownRecent(user.id(),"APPROVED");
        var all=new LinkedHashMap<Long,TeachingResource>(); for(var list:List.of(recent,rejected,published)) for(var r:list) all.put(r.id(),r);
        Map<Long,ResourceRow> rows=new HashMap<>(); repository.rows(new ArrayList<>(all.values())).forEach(r->rows.put(r.resource().id(),r));
        return new TeacherDashboard(repository.ownCounts(user.id()),recent.stream().map(r->rows.get(r.id())).toList(),rejected.stream().map(r->rows.get(r.id())).toList(),published.stream().map(r->rows.get(r.id())).toList());
    }
    public List<ResourceRow> ownPresentations(UserPrincipal user,List<Long> ids) {
        role(user,"TEACHER"); if(ids==null||ids.size()>100||ids.stream().anyMatch(i->i==null||i<=0))throw bad("资源ID列表不正确");
        var unique=ids.stream().distinct().toList();var rows=repository.owned(user.id(),unique);
        if(rows.size()!=unique.size())throw notFound();return repository.rows(rows);
    }
    public ResourceTable ledger(UserPrincipal user,String keyword,Long course,Long category,Long teacher,int page,int size) {
        role(user,"ADMIN");page(page,size);
        if(keyword!=null&&keyword.length()>200||invalid(course)||invalid(category)||invalid(teacher))throw bad("筛选参数不正确");
        return repository.ledger(keyword,course,category,teacher,page,size);
    }
    public List<ResourceRow> pendingRecent(UserPrincipal user) {
        role(user,"ADMIN"); return repository.rows(repository.pendingRecent());
    }
    private static boolean invalid(Long id){return id!=null&&id<=0;}
    private static void id(long id){if(id<=0)throw bad("ID不正确");}
    private static void page(int page,int size){if(page<1||size<1||size>100)throw bad("分页参数不正确");}
    private static void role(UserPrincipal user,String... allowed) {
        if(user==null)throw new BusinessException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请先登录");
        if(!Arrays.asList(allowed).contains(user.role()))throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","没有操作权限");
    }
    private static BusinessException bad(String message){return new BusinessException(HttpStatus.BAD_REQUEST,"BAD_REQUEST",message);}
    private static BusinessException notFound(){return new BusinessException(HttpStatus.NOT_FOUND,"NOT_FOUND","资源或导航内容不存在或不可访问");}
}
