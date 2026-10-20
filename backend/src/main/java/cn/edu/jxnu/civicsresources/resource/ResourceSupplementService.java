package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.edu.jxnu.civicsresources.resource.FavoriteFolderService.failure;

@Service
public class ResourceSupplementService {
 private final JdbcTemplate jdbc;private final ResourceDraftRepository drafts;private final PublishedResourceRepository published;private final CoverImageStorage covers;
 public ResourceSupplementService(JdbcTemplate jdbc,ResourceDraftRepository drafts,PublishedResourceRepository published,CoverImageStorage covers){this.jdbc=jdbc;this.drafts=drafts;this.published=published;this.covers=covers;}
 public record MetadataRequest(String originalAuthor,String sourceNote,String usageRights){}
 public record Metadata(String originalAuthor,String sourceNote,String usageRights,boolean hasCover,LocalDateTime updatedAt){}
 private record Stored(String author,String source,String rights,String key,int size,LocalDateTime updated){}
 private Stored stored(long id){return jdbc.query("SELECT * FROM resource_metadata WHERE resource_id=?",(rs,n)->new Stored(rs.getString("original_author"),rs.getString("source_note"),rs.getString("usage_rights"),rs.getString("cover_key"),rs.getInt("cover_size"),rs.getTimestamp("updated_at").toLocalDateTime()),id).stream().findFirst().orElse(new Stored(null,null,null,null,0,null));}
 private TeachingResource access(UserPrincipal u,long id,String scope,boolean edit){
  if(u==null)throw failure(401,"请先登录");if(id<=0)throw failure(400,"资源ID不正确");TeachingResource r;
  if("teacher".equals(scope)){if(!"TEACHER".equals(u.role()))throw failure(403,"仅教师可操作本人资源");r=drafts.findOwned(id,u.id(),true).orElseThrow(()->failure(404,"资源不存在或不可访问"));}
  else if("review".equals(scope)){if(!"ADMIN".equals(u.role()))throw failure(403,"仅管理员可查看审核材料");r=drafts.findForReview(id,true).filter(x->x.submissionNo()>0&&Set.of("PENDING","REJECTED","APPROVED").contains(x.status())).orElseThrow(()->failure(404,"审核材料不存在"));}
  else{FavoriteFolderService.consumer(u);if(!published.lockVisible(id))throw failure(404,"资源不存在或不可访问");r=published.findVisible(id,u.id()).orElseThrow(()->failure(404,"资源不存在或不可访问")).resource();}
  if(edit&&!Set.of("DRAFT","REJECTED").contains(r.status()))throw failure(409,"仅本人草稿或驳回资源可以修改封面和来源说明");return r;
 }
 @Transactional public Metadata metadata(UserPrincipal u,long id,String scope){access(u,id,scope,false);var s=stored(id);return new Metadata(s.author,s.source,s.rights,s.key!=null,s.updated);}
 static String text(String value,int limit){if(value==null||value.isBlank())return null;String s=value.strip();if(s.length()>limit||s.indexOf(0)>=0)throw failure(400,"资源说明过长或含无效字符");return s;}
 @Transactional public void save(UserPrincipal u,long id,MetadataRequest r){access(u,id,"teacher",true);if(r==null)throw failure(400,"请填写说明");jdbc.update("INSERT INTO resource_metadata(resource_id,original_author,source_note,usage_rights) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE original_author=VALUES(original_author),source_note=VALUES(source_note),usage_rights=VALUES(usage_rights)",id,text(r.originalAuthor,120),text(r.sourceNote,1000),text(r.usageRights,1000));touch(id);}
 @Transactional public void cover(UserPrincipal u,long id,MultipartFile file){access(u,id,"teacher",true);var old=stored(id);var saved=covers.save(file);compensate(saved.key(),old.key);jdbc.update("INSERT INTO resource_metadata(resource_id,cover_key,cover_size) VALUES (?,?,?) ON DUPLICATE KEY UPDATE cover_key=VALUES(cover_key),cover_size=VALUES(cover_size)",id,saved.key(),saved.size());touch(id);}
 @Transactional public void removeCover(UserPrincipal u,long id){access(u,id,"teacher",true);var old=stored(id);jdbc.update("UPDATE resource_metadata SET cover_key=NULL,cover_size=0 WHERE resource_id=?",id);compensate(null,old.key);touch(id);}
 @Transactional public byte[] cover(UserPrincipal u,long id,String scope){access(u,id,scope,false);var s=stored(id);return covers.read(s.key,s.size);}
 private void touch(long id){jdbc.update("UPDATE teaching_resource SET updated_at=CURRENT_TIMESTAMP(3) WHERE id=?",id);}
 private void compensate(String newKey,String oldKey){TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)covers.discard(newKey);}@Override public void afterCommit(){covers.discard(oldKey);}});}
}
