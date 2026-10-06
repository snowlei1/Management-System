package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** SQL fixtures are rolled back; no files or permanent test records are created. */
@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="STAGE7_MYSQL_TEST",matches="true")
class ResourcePortalMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ResourcePortalService portal;
    String tag; long course,category,element,otherCourse; UserPrincipal teacher,student,admin,other;
    UserPrincipal principal(String name,String role){return new UserPrincipal(jdbc.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,name),name,"unused",role,"ACTIVE");}
    long named(String table,String name){return jdbc.queryForObject("SELECT id FROM "+table+" WHERE name=?",Long.class,name);}
    @BeforeEach void setup(){
        tag="S7-TX-"+UUID.randomUUID().toString().substring(0,8);
        teacher=principal("dev_teacher","TEACHER");other=principal("dev_teacher_b","TEACHER");student=principal("dev_student","STUDENT");admin=principal("dev_admin","ADMIN");
        jdbc.update("INSERT INTO course(course_code,name) VALUES (?,?)",tag,tag);course=named("course",tag);
        jdbc.update("INSERT INTO course(course_code,name) VALUES (?,?)",tag+"b",tag+"b");otherCourse=named("course",tag+"b");
        jdbc.update("INSERT INTO resource_category(name) VALUES (?)",tag);category=named("resource_category",tag);
        jdbc.update("INSERT INTO ideological_element(name) VALUES (?)",tag);element=named("ideological_element",tag);
    }
    long resource(String state,boolean deleted,long owner,long courseId){
        jdbc.update("""
            INSERT INTO teaching_resource(title,course_id,category_id,created_by,file_storage_key,file_original_name,
            file_mime_type,file_size_bytes,status,submission_no,published_at,deleted_at)
            VALUES (?,?,?,?,?,'夹具.pdf','application/pdf',1,?,?,?,?)
            """,tag,courseId,category,owner,UUID.randomUUID()+".pdf",state,"DRAFT".equals(state)?0:1,
                "APPROVED".equals(state)?java.sql.Timestamp.valueOf("2026-10-06 09:00:00"):null,
                deleted?java.sql.Timestamp.valueOf("2026-10-06 10:00:00"):null);
        return jdbc.queryForObject("SELECT MAX(id) FROM teaching_resource WHERE title=?",Long.class,tag);
    }
    long resource(String state){return resource(state,false,teacher.id(),course);}
    void relate(long id){jdbc.update("INSERT INTO resource_element_relation(resource_id,element_id) VALUES (?,?)",id,element);}
    @Test void navigationUsesVisibleActiveDataAndRetainsZeroItems(){
        long approved=resource("APPROVED"),draft=resource("DRAFT"),deleted=resource("APPROVED",true,teacher.id(),course);
        for(long id:List.of(approved,draft,deleted))relate(id);
        var c=portal.navigation(student,false).stream().filter(n->n.id()==course).findFirst().orElseThrow();assertEquals(1,c.resourceCount());
        assertEquals(0,portal.navigation(student,false).stream().filter(n->n.id()==otherCourse).findFirst().orElseThrow().resourceCount());
        assertEquals(1,portal.navigationDetail(student,false,course).related().getFirst().resourceCount());
        assertTrue(portal.navigationDetail(student,true,element).related().stream().anyMatch(n->n.id()==course));
        jdbc.update("UPDATE course SET status='INACTIVE' WHERE id=?",course);
        assertFalse(portal.navigation(student,false).stream().anyMatch(n->n.id()==course));assertThrows(BusinessException.class,()->portal.navigationDetail(student,false,course));
    }
    @Test void relatedResourcesAreFixedUniqueAndOrderedWithoutEventWrites(){
        long id=resource("APPROVED"),same=resource("APPROVED"),cross=resource("APPROVED",false,other.id(),otherCourse),hidden=resource("PENDING");
        for(long r:List.of(id,same,cross,hidden))relate(r);
        var p=portal.presentation(student,id);assertTrue(p.sameCourse().stream().anyMatch(r->r.id()==same));assertFalse(p.sameCourse().stream().anyMatch(r->r.id()==cross));
        assertTrue(p.sameElements().stream().anyMatch(r->r.id()==cross));assertFalse(p.sameElements().stream().anyMatch(r->r.id()==id||r.id()==hidden));
        assertEquals(0,p.usage().browseEvents());assertEquals(0,p.usage().downloadRequests());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM browse_record WHERE resource_id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM download_record WHERE resource_id=?",Integer.class,id));
    }
    @Test void historyIsUserScopedEventBasedPagedAndFiltersRemovedResources(){
        long id=resource("APPROVED"),hidden=resource("DRAFT"),deleted=resource("APPROVED",true,teacher.id(),course);
        for(String table:List.of("browse_record","download_record")){
            for(long r:List.of(id,id,hidden,deleted))jdbc.update("INSERT INTO "+table+"(user_id,resource_id) VALUES (?,?)",student.id(),r);
            jdbc.update("INSERT INTO "+table+"(user_id,resource_id) VALUES (?,?)",teacher.id(),id);
        }
        for(String kind:List.of("browse","downloads")){
            var first=portal.history(student,kind,1,1);var second=portal.history(student,kind,2,1);
            assertEquals(id,first.items().getFirst().resourceId());assertTrue(first.items().getFirst().id()>second.items().getFirst().id());
            assertFalse(portal.history(student,kind,1,100).items().stream().anyMatch(r->r.resourceId()==hidden||r.resourceId()==deleted));
            assertEquals(2,portal.history(student,kind,1,100).items().stream().filter(r->r.resourceId()==id).count());
            assertEquals(1,portal.history(teacher,kind,1,100).items().stream().filter(r->r.resourceId()==id).count());
        }
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM browse_record WHERE user_id=? AND resource_id IN (?,?,?)",Integer.class,student.id(),id,hidden,deleted));
    }
    @Test void usageCountsHistoricalEventsButOnlyCurrentPublicFavorites(){
        long id=resource("APPROVED");jdbc.update("INSERT INTO browse_record(user_id,resource_id) VALUES (?,?),(?,?)",student.id(),id,teacher.id(),id);
        jdbc.update("INSERT INTO download_record(user_id,resource_id) VALUES (?,?)",student.id(),id);
        jdbc.update("INSERT INTO favorite(user_id,resource_id,active) VALUES (?,?,TRUE),(?,?,FALSE)",student.id(),id,teacher.id(),id);
        var u=portal.presentation(student,id).usage();assertEquals(2,u.browseEvents());assertEquals(1,u.downloadRequests());assertEquals(1,u.currentFavorites());
    }
    @Test void teacherDashboardOwnershipLatestAuditAndBatchIsolation(){
        long id=resource("REJECTED");resource("DRAFT");long otherId=resource("DRAFT",false,other.id(),course);
        jdbc.update("INSERT INTO audit_record(resource_id,submission_no,reviewer_id,decision,reason) VALUES (?,1,?,'REJECT','补充依据')",id,admin.id());
        var rows=portal.ownPresentations(teacher,List.of(id,id));assertEquals(1,rows.size());assertEquals("补充依据",rows.getFirst().latestAudit().reason());
        assertThrows(BusinessException.class,()->portal.ownPresentations(teacher,List.of(id,otherId)));
        var dashboard=portal.dashboard(teacher);assertTrue(dashboard.rejected().stream().anyMatch(r->r.resource().id()==id));
        assertFalse(dashboard.recent().stream().anyMatch(r->r.resource().createdBy()!=teacher.id()));
        assertTrue(dashboard.counts().get("DRAFT")>=1);assertEquals(List.of(),portal.ownPresentations(teacher,List.of()));
    }
    @Test void ledgerFiltersLiteralSearchAndKeepsReviewMetadata(){
        long id=resource("APPROVED");relate(id);resource("PENDING");resource("APPROVED",true,teacher.id(),course);
        var p=portal.ledger(admin,tag,course,category,teacher.id(),1,1);assertEquals(1,p.total());assertEquals(id,p.items().getFirst().resource().id());
        assertEquals(1,p.items().getFirst().resource().elements().size());assertEquals(0,portal.ledger(admin,"' OR 1=1 --",course,category,teacher.id(),1,10).total());
        assertEquals(0,portal.ledger(admin,tag+"%",course,category,teacher.id(),1,10).total());
    }
    @Test void pendingOverviewReallySelectsLatestSubmissionTimes(){
        var ids=new ArrayList<Long>();for(int i=0;i<6;i++)ids.add(resource("PENDING"));
        for(int i=0;i<6;i++)jdbc.update("UPDATE teaching_resource SET updated_at=? WHERE id=?",java.sql.Timestamp.valueOf("2099-01-01 10:00:0"+i),ids.get(i));
        var result=portal.pendingRecent(admin);assertEquals(5,result.size());assertEquals(ids.getLast(),result.getFirst().resource().id());
        assertFalse(result.stream().anyMatch(r->r.resource().id()==ids.getFirst()));assertTrue(result.stream().allMatch(r->r.resource().pendingSubmittedAt()!=null));
    }
    @Test void nonPublishedPresentationNotFound(){for(String state:List.of("DRAFT","PENDING","REJECTED")){long id=resource(state);assertEquals(404,assertThrows(BusinessException.class,()->portal.presentation(student,id)).status().value());}}
}
