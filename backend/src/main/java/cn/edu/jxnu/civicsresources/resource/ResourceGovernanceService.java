package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.edu.jxnu.civicsresources.resource.FavoriteFolderService.failure;

@Service
public class ResourceGovernanceService {
 private final JdbcTemplate jdbc;private final ResourceDraftRepository drafts;private final LocalResourceFileStorage files;
 public ResourceGovernanceService(JdbcTemplate jdbc,ResourceDraftRepository drafts,LocalResourceFileStorage files){this.jdbc=jdbc;this.drafts=drafts;this.files=files;}
 public record FeedbackRequest(String kind,String description){}
 public record HandleRequest(String status,String response){}
 public record PublicationRequest(String reason){}
 public record PublicationEvent(long id,String action,String reason,String actorName,LocalDateTime occurredAt){}
 public record Publication(long resourceId,String title,LocalDateTime withdrawnAt,List<PublicationEvent> events){}
 public record Feedback(long id,long resourceId,String title,String kind,String description,String status,String response,String reporterName,String handlerName,LocalDateTime createdAt,LocalDateTime handledAt){}
 public record FeedbackPage(List<Feedback> items,long total,int page,int size){}
 private static void admin(UserPrincipal u){if(u==null)throw failure(401,"请先登录");if(!"ADMIN".equals(u.role()))throw failure(403,"仅管理员可以处理资源治理事项");}
 private TeachingResource approved(UserPrincipal u,long id){admin(u);if(id<=0)throw failure(400,"资源ID不正确");return drafts.findForReview(id,true).filter(r->"APPROVED".equals(r.status())&&r.publishedAt()!=null&&r.submissionNo()>0).orElseThrow(()->failure(404,"已审核通过的资源不存在"));}
 private LocalDateTime withdrawn(long id){return jdbc.queryForObject("SELECT withdrawn_at FROM teaching_resource WHERE id=?",(rs,n)->rs.getTimestamp(1)==null?null:rs.getTimestamp(1).toLocalDateTime(),id);}
 @Transactional public Publication publication(UserPrincipal u,long id){var r=approved(u,id);var events=jdbc.query("SELECT p.id,p.action,p.reason,u.display_name,p.occurred_at FROM publication_event p JOIN app_user u ON u.id=p.actor_id WHERE p.resource_id=? ORDER BY p.id",(rs,n)->new PublicationEvent(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toLocalDateTime()),id);return new Publication(id,r.title(),withdrawn(id),events);}
 @Transactional public Publication change(UserPrincipal u,long id,boolean down,PublicationRequest request){
  var r=approved(u,id);String reason=ResourceSupplementService.text(request==null?null:request.reason(),1000);
  if(down&&reason==null)throw failure(400,"下架原因必填，最多1000个字符");
  if((withdrawn(id)!=null)==down)throw failure(409,"发布状态已变化，请刷新后操作");
  if(!down)files.verify(r);
  jdbc.update("UPDATE teaching_resource SET withdrawn_at="+(down?"CURRENT_TIMESTAMP(3)":"NULL")+",updated_at=CURRENT_TIMESTAMP(3) WHERE id=?",id);
  jdbc.update("INSERT INTO publication_event(resource_id,actor_id,action,reason) VALUES (?,?,?,?)",id,u.id(),down?"WITHDRAW":"RESTORE",reason);
  return publication(u,id);
 }
 @Transactional public void report(UserPrincipal u,long id,FeedbackRequest request){
  FavoriteFolderService.consumer(u);if(id<=0||jdbc.query("SELECT id FROM teaching_resource r WHERE id=? AND "+ResourceVisibility.PUBLISHED+" FOR UPDATE",(rs,n)->rs.getLong(1),id).isEmpty())throw failure(404,"资源不存在或不可使用");
  if(request==null||request.kind()==null||!Set.of("FILE","CONTENT","CLASSIFICATION","RIGHTS","OTHER").contains(request.kind()))throw failure(400,"反馈类型不正确");
  String description=ResourceSupplementService.text(request.description(),1000);if(description==null)throw failure(400,"请填写问题说明");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM resource_feedback WHERE resource_id=? AND user_id=? AND status='OPEN'",Long.class,id,u.id())>0)throw failure(409,"该资源已有本人未处理反馈，请等待管理员处理");
  jdbc.update("INSERT INTO resource_feedback(resource_id,user_id,kind,description) VALUES (?,?,?,?)",id,u.id(),request.kind(),description);
 }
 public FeedbackPage feedback(UserPrincipal u,boolean management,String status,Long resource,int page,int size){
  if(management)admin(u);else FavoriteFolderService.consumer(u);
  if(page<1||size<1||size>100||resource!=null&&resource<=0||status!=null&&!Set.of("ALL","OPEN","RESOLVED","DISMISSED").contains(status))throw failure(400,"反馈筛选参数不正确");
  String where=" WHERE 1=1";List<Object> args=new ArrayList<>();
  if(!management){where+=" AND f.user_id=?";args.add(u.id());}if(status!=null&&!"ALL".equals(status)){where+=" AND f.status=?";args.add(status);}if(resource!=null){where+=" AND f.resource_id=?";args.add(resource);}
  String from=" FROM resource_feedback f JOIN teaching_resource r ON r.id=f.resource_id JOIN app_user reporter ON reporter.id=f.user_id LEFT JOIN app_user handler ON handler.id=f.handled_by";
  long total=jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,args.toArray());args.add(size);args.add(((long)page-1)*size);
  var items=jdbc.query("SELECT f.*,r.title,reporter.display_name reporter_name,handler.display_name handler_name"+from+where+" ORDER BY f.id DESC LIMIT ? OFFSET ?",(rs,n)->new Feedback(rs.getLong("id"),rs.getLong("resource_id"),rs.getString("title"),rs.getString("kind"),rs.getString("description"),rs.getString("status"),rs.getString("response"),rs.getString("reporter_name"),rs.getString("handler_name"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("handled_at")==null?null:rs.getTimestamp("handled_at").toLocalDateTime()),args.toArray());
  return new FeedbackPage(items,total,page,size);
 }
 @Transactional public void handle(UserPrincipal u,long id,HandleRequest request){
  admin(u);if(id<=0||request==null||request.status()==null||!Set.of("RESOLVED","DISMISSED").contains(request.status()))throw failure(400,"处理结果不正确");String response=ResourceSupplementService.text(request.response(),1000);if(response==null)throw failure(400,"请填写处理说明");
  var rows=jdbc.query("SELECT status FROM resource_feedback WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1),id);if(rows.isEmpty())throw failure(404,"反馈不存在");if(!"OPEN".equals(rows.getFirst()))throw failure(409,"反馈已处理，不允许覆盖原结论");
  jdbc.update("UPDATE resource_feedback SET status=?,response=?,handled_by=?,handled_at=CURRENT_TIMESTAMP(3) WHERE id=?",request.status(),response,u.id(),id);
 }
}
