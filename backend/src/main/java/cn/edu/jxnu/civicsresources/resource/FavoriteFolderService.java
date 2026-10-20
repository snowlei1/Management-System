package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteFolderService {
    private final JdbcTemplate jdbc;
    private final PublishedResourceRepository resources;
    public FavoriteFolderService(JdbcTemplate jdbc, PublishedResourceRepository resources) { this.jdbc=jdbc;this.resources=resources; }
    public record Folder(long id,String name) { }
    public record NameRequest(String name) { }
    public record MoveRequest(Long folderId) { }
    static void consumer(UserPrincipal u) { if(u==null)throw failure(401,"请先登录");if(!Set.of("TEACHER","STUDENT").contains(u.role()))throw failure(403,"仅教师和学生可以使用私人收藏夹"); }
    static BusinessException failure(int status,String message){return new BusinessException(HttpStatus.valueOf(status),"RESOURCE_OPERATION_FAILED",message);}
    public List<Folder> list(UserPrincipal u) { consumer(u);return jdbc.query("SELECT id,name FROM favorite_folder WHERE user_id=? ORDER BY id",(rs,n)->new Folder(rs.getLong(1),rs.getString(2)),u.id()); }
    static String normalizeName(String name){String value=name==null?"":name.strip();if(value.isBlank()||value.length()>80)throw failure(400,"收藏夹名称须为1—80个字符");return value;}
    @Transactional public Folder create(UserPrincipal u,NameRequest request){
        consumer(u);String name=normalizeName(request==null?null:request.name());var key=new GeneratedKeyHolder();
        try{jdbc.update(c->{var s=c.prepareStatement("INSERT INTO favorite_folder(user_id,name) VALUES (?,?)",new String[]{"id"});s.setLong(1,u.id());s.setString(2,name);return s;},key);}
        catch(DuplicateKeyException e){throw failure(409,"本人已有同名收藏夹");}
        return new Folder(Objects.requireNonNull(key.getKey()).longValue(),name);
    }
    private void owned(long user,long id){if(id<=0||jdbc.query("SELECT id FROM favorite_folder WHERE id=? AND user_id=? FOR UPDATE",(rs,n)->rs.getLong(1),id,user).isEmpty())throw failure(404,"收藏夹不存在或不可访问");}
    @Transactional public Folder rename(UserPrincipal u,long id,NameRequest request){consumer(u);String name=normalizeName(request==null?null:request.name());owned(u.id(),id);try{jdbc.update("UPDATE favorite_folder SET name=? WHERE id=? AND user_id=?",name,id,u.id());}catch(DuplicateKeyException e){throw failure(409,"本人已有同名收藏夹");}return new Folder(id,name);}
    @Transactional public void remove(UserPrincipal u,long id){consumer(u);owned(u.id(),id);jdbc.update("UPDATE favorite SET folder_id=NULL WHERE user_id=? AND folder_id=?",u.id(),id);jdbc.update("DELETE FROM favorite_folder WHERE id=? AND user_id=?",id,u.id());}
    @Transactional public void move(UserPrincipal u,long resource,MoveRequest request){
        consumer(u);if(resource<=0||!resources.lockVisible(resource))throw failure(404,"资源不存在或不可使用");
        Long folder=request==null?null:request.folderId();if(folder!=null)owned(u.id(),folder);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE user_id=? AND resource_id=? AND active=TRUE",Long.class,u.id(),resource)==0)throw failure(404,"请先收藏该资源");
        jdbc.update("UPDATE favorite SET folder_id=? WHERE user_id=? AND resource_id=? AND active=TRUE",folder,u.id(),resource);
    }
}
