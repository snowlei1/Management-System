package cn.edu.jxnu.civicsresources.resource;

import static cn.edu.jxnu.civicsresources.resource.ResourceReadModels.*;
import java.util.*;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ResourcePortalRepository {
    static final String VISIBLE = "r.status='APPROVED' AND r.deleted_at IS NULL AND r.published_at IS NOT NULL";
    private static final String FROM = " FROM teaching_resource r JOIN course c ON c.id=r.course_id "
            + "JOIN resource_category k ON k.id=r.category_id JOIN app_user u ON u.id=r.created_by ";
    private static final String SELECT = "SELECT r.*,c.name course_name,c.status course_status,k.name category_name,"
            + "k.status category_status,u.display_name teacher_name" + FROM;
    private final NamedParameterJdbcTemplate jdbc;
    private final ResourceDraftRepository metadata;
    public ResourcePortalRepository(NamedParameterJdbcTemplate jdbc, ResourceDraftRepository metadata) {
        this.jdbc = jdbc; this.metadata = metadata;
    }
    public List<NavigationItem> navigation(boolean topics, Long course, Long element) {
        // Only fixed internal table/column fragments are substituted; all request values are bound.
        String table = topics ? "ideological_element" : "course";
        String join = topics ? " LEFT JOIN resource_element_relation x ON x.element_id=b.id LEFT JOIN teaching_resource r ON r.id=x.resource_id AND "
                : " LEFT JOIN teaching_resource r ON r.course_id=b.id AND ";
        String code = topics ? "NULL" : "b.course_code";
        String condition = ""; var p = new MapSqlParameterSource();
        if (course != null) { condition += " AND r.course_id=:course"; p.addValue("course", course); }
        if (element != null) {
            condition += " AND EXISTS (SELECT 1 FROM resource_element_relation y WHERE y.resource_id=r.id AND y.element_id=:element)";
            p.addValue("element", element);
        }
        String having = course != null || element != null ? " HAVING COUNT(r.id)>0" : "";
        return jdbc.query("SELECT b.id," + code + " code,b.name,b.description,b.status,COUNT(r.id) resource_count FROM "
                + table + " b" + join + VISIBLE + condition + " WHERE b.status='ACTIVE' GROUP BY b.id" + having
                + " ORDER BY resource_count DESC,b.id ASC", p, (rs,n) -> new NavigationItem(rs.getLong("id"),
                    rs.getString("code"),rs.getString("name"),rs.getString("description"),rs.getString("status"),rs.getLong("resource_count")));
    }
    public boolean visible(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM teaching_resource r WHERE r.id=:id AND " + VISIBLE, Map.of("id",id),Long.class) > 0;
    }
    public Map<Long, Usage> usage(List<Long> ids) {
        Map<Long, Usage> result = new LinkedHashMap<>(); ids.forEach(id -> result.put(id, Usage.zero()));
        if (ids.isEmpty()) return result;
        var p = Map.of("ids",ids);
        jdbc.query("SELECT resource_id,COUNT(*) n FROM browse_record WHERE resource_id IN (:ids) GROUP BY resource_id", p,
                (RowCallbackHandler) rs -> { long id=rs.getLong(1); var old=result.get(id); result.put(id,new Usage(rs.getLong(2),old.downloadRequests(),old.currentFavorites())); });
        jdbc.query("SELECT resource_id,COUNT(*) n FROM download_record WHERE resource_id IN (:ids) GROUP BY resource_id", p,
                (RowCallbackHandler) rs -> { long id=rs.getLong(1); var old=result.get(id); result.put(id,new Usage(old.browseEvents(),rs.getLong(2),old.currentFavorites())); });
        jdbc.query("SELECT f.resource_id,COUNT(*) n FROM favorite f JOIN teaching_resource r ON r.id=f.resource_id WHERE f.resource_id IN (:ids) AND f.active=TRUE AND "
                + VISIBLE + " GROUP BY f.resource_id", p, (RowCallbackHandler) rs -> { long id=rs.getLong(1); var old=result.get(id); result.put(id,new Usage(old.browseEvents(),old.downloadRequests(),rs.getLong(2))); });
        return result;
    }
    public List<PublishedResourceView> related(long id, long user, boolean elements) {
        String relation = elements ? "EXISTS (SELECT 1 FROM resource_element_relation a JOIN resource_element_relation b ON b.element_id=a.element_id WHERE a.resource_id=:id AND b.resource_id=r.id)"
                : "r.course_id=(SELECT course_id FROM teaching_resource WHERE id=:id)";
        var rows = jdbc.query(SELECT + " WHERE " + VISIBLE + " AND r.id<>:id AND " + relation
                + " ORDER BY r.published_at DESC,r.id DESC LIMIT 6", Map.of("id",id), (rs,n) -> ResourceDraftRepository.map(rs));
        var tags=metadata.elementsFor(rows.stream().map(TeachingResource::id).toList());
        Set<Long> favorites=new HashSet<>();
        if (!rows.isEmpty()) jdbc.query("SELECT resource_id FROM favorite WHERE user_id=:user AND active=TRUE AND resource_id IN (:ids)",
                Map.of("user",user,"ids",rows.stream().map(TeachingResource::id).toList()), (RowCallbackHandler) rs -> favorites.add(rs.getLong(1)));
        return rows.stream().map(r -> PublishedResourceView.from(new PublishedResourceRepository.Row(r,favorites.contains(r.id())),tags.getOrDefault(r.id(),List.of()))).toList();
    }
    public HistoryPage history(long user, boolean downloads, int page, int size) {
        String table=downloads?"download_record":"browse_record", time=downloads?"downloaded_at":"browsed_at";
        String from=" FROM " + table + " h JOIN teaching_resource r ON r.id=h.resource_id JOIN course c ON c.id=r.course_id WHERE h.user_id=:user AND " + VISIBLE;
        var p=new MapSqlParameterSource("user",user).addValue("limit",size).addValue("offset",((long)page-1)*size);
        long count=jdbc.queryForObject("SELECT COUNT(*)"+from,p,Long.class);
        var items=jdbc.query("SELECT h.id,r.id resource_id,r.title,r.course_id,c.name course_name,r.file_original_name,r.file_mime_type,h."+time+" accessed_at"+from
                +" ORDER BY h."+time+" DESC,h.id DESC LIMIT :limit OFFSET :offset",p,(rs,n)->new HistoryItem(rs.getLong("id"),rs.getLong("resource_id"),rs.getString("title"),rs.getLong("course_id"),rs.getString("course_name"),rs.getString("file_original_name"),rs.getString("file_mime_type"),rs.getTimestamp("accessed_at").toLocalDateTime()));
        return new HistoryPage(items,count,page,size);
    }
    public Map<String,Long> ownCounts(long owner) {
        Map<String,Long> counts=new LinkedHashMap<>(); for(String state:List.of("DRAFT","PENDING","REJECTED","APPROVED")) counts.put(state,0L);
        jdbc.query("SELECT r.status,COUNT(*) n FROM teaching_resource r WHERE r.created_by=:owner AND r.deleted_at IS NULL GROUP BY r.status",Map.of("owner",owner),
                (RowCallbackHandler) rs->counts.put(rs.getString(1),rs.getLong(2)));
        return counts;
    }
    public List<TeachingResource> ownRecent(long owner, String state) {
        String condition=state==null?"":" AND r.status=:state";
        if("APPROVED".equals(state)) condition+=" AND r.published_at IS NOT NULL";
        return jdbc.query(SELECT+" WHERE r.created_by=:owner AND r.deleted_at IS NULL"+condition+" ORDER BY r.updated_at DESC,r.id DESC LIMIT 5",
                new MapSqlParameterSource("owner",owner).addValue("state",state),(rs,n)->ResourceDraftRepository.map(rs));
    }
    public List<TeachingResource> owned(long owner, List<Long> ids) {
        if(ids.isEmpty()) return List.of();
        return jdbc.query(SELECT+" WHERE r.created_by=:owner AND r.deleted_at IS NULL AND r.id IN (:ids)",Map.of("owner",owner,"ids",ids),(rs,n)->ResourceDraftRepository.map(rs));
    }
    public List<TeachingResource> pendingRecent() {
        return jdbc.query(SELECT + " WHERE r.status='PENDING' AND r.deleted_at IS NULL AND r.submission_no>0"
                + " ORDER BY r.updated_at DESC,r.id DESC LIMIT 5", Map.of(), (rs,n) -> ResourceDraftRepository.map(rs));
    }
    public List<ResourceRow> rows(List<TeachingResource> rows) {
        List<Long> ids=rows.stream().map(TeachingResource::id).toList(); if(ids.isEmpty()) return List.of();
        var tags=metadata.elementsFor(ids); var uses=usage(ids); Map<Long,AuditRecordView> audits=new HashMap<>();
        jdbc.query("SELECT a.*,u.display_name reviewer_name FROM audit_record a JOIN app_user u ON u.id=a.reviewer_id "
                + "WHERE a.resource_id IN (:ids) AND a.submission_no=(SELECT MAX(b.submission_no) FROM audit_record b WHERE b.resource_id=a.resource_id)",Map.of("ids",ids),
                (RowCallbackHandler) rs -> audits.put(rs.getLong("resource_id"),new AuditRecordView(rs.getLong("id"),rs.getLong("submission_no"),rs.getLong("reviewer_id"),rs.getString("reviewer_name"),rs.getString("from_status"),rs.getString("decision"),rs.getString("reason"),rs.getTimestamp("audited_at").toLocalDateTime())));
        return rows.stream().map(r->new ResourceRow(ResourceDraftView.from(r,tags.getOrDefault(r.id(),List.of())),uses.get(r.id()),audits.get(r.id()))).toList();
    }
    public ResourceTable ledger(String keyword, Long course, Long category, Long teacher, int page, int size) {
        StringBuilder where=new StringBuilder(" WHERE "+VISIBLE+" AND r.submission_no>0"); var p=new MapSqlParameterSource();
        if(keyword!=null&&!keyword.isBlank()) { where.append(" AND r.title LIKE :keyword ESCAPE '!'"); p.addValue("keyword","%"+keyword.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%"); }
        if(course!=null){where.append(" AND r.course_id=:course");p.addValue("course",course);}
        if(category!=null){where.append(" AND r.category_id=:category");p.addValue("category",category);}
        if(teacher!=null){where.append(" AND r.created_by=:teacher");p.addValue("teacher",teacher);}
        long total=jdbc.queryForObject("SELECT COUNT(*)"+FROM+where,p,Long.class);
        p.addValue("limit",size).addValue("offset",((long)page-1)*size);
        var rows=jdbc.query(SELECT+where+" ORDER BY r.published_at DESC,r.id DESC LIMIT :limit OFFSET :offset",p,(rs,n)->ResourceDraftRepository.map(rs));
        return new ResourceTable(rows(rows),total,page,size);
    }
}
