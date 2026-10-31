package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import cn.edu.jxnu.civicsresources.statistics.StatisticsService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Real MySQL transactions roll back every fixture. File integrity is exercised by the HTTP suite. */
@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="GOVERNANCE_MYSQL_TEST",matches="true")
class ResourceGovernanceMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceGovernanceService governance;
    @Autowired PublishedResourceService published;
    @Autowired ResourcePortalService portal;
    @Autowired ResourceSupplementService supplements;
    @Autowired StatisticsService statistics;
    @MockitoBean LocalResourceFileStorage files;
    UserPrincipal admin,teacher,student;
    long id,course,category,element;
    String tag;
    UserPrincipal principal(String name,String role){return new UserPrincipal(jdbc.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,name),name,"unused",role,"ACTIVE");}
    @BeforeEach void setup(){
        tag="Governance-TX-"+UUID.randomUUID().toString().substring(0,8);
        admin=principal("dev_admin","ADMIN");teacher=principal("dev_teacher","TEACHER");student=principal("dev_student","STUDENT");
        jdbc.update("INSERT INTO course(course_code,name) VALUES (?,?)",tag,tag);
        course=jdbc.queryForObject("SELECT id FROM course WHERE course_code=?",Long.class,tag);
        jdbc.update("INSERT INTO resource_category(name) VALUES (?)",tag);category=jdbc.queryForObject("SELECT id FROM resource_category WHERE name=?",Long.class,tag);
        jdbc.update("INSERT INTO ideological_element(name) VALUES (?)",tag);element=jdbc.queryForObject("SELECT id FROM ideological_element WHERE name=?",Long.class,tag);
        jdbc.update("""
            INSERT INTO teaching_resource(title,course_id,category_id,created_by,file_storage_key,file_original_name,
            file_mime_type,file_size_bytes,status,submission_no,published_at)
            VALUES (?,?,?,?,?,'fixture.pdf','application/pdf',1,'APPROVED',1,'2026-10-01 09:00:00')
            """,tag,course,category,teacher.id(),UUID.randomUUID()+".pdf");
        id=jdbc.queryForObject("SELECT id FROM teaching_resource WHERE title=?",Long.class,tag);
        jdbc.update("INSERT INTO resource_element_relation(resource_id,element_id) VALUES (?,?)",id,element);
        jdbc.update("INSERT INTO audit_record(resource_id,submission_no,reviewer_id,decision) VALUES (?,1,?,'APPROVE')",id,admin.id());
    }
    void error(int status,org.junit.jupiter.api.function.Executable action){assertEquals(status,assertThrows(BusinessException.class,action).status().value());}
    void withdraw(){governance.change(admin,id,true,new ResourceGovernanceService.PublicationRequest("核查授权"));}
    @Test void withdrawalRestorationPreservesAuditAndOriginalPublication(){
        var before=jdbc.queryForMap("SELECT published_at,file_storage_key,status FROM teaching_resource WHERE id=?",id);
        withdraw();assertNotNull(governance.publication(admin,id).withdrawnAt());
        error(409,()->withdraw());
        var restored=governance.change(admin,id,false,new ResourceGovernanceService.PublicationRequest(null));
        assertNull(restored.withdrawnAt());assertEquals(List.of("WITHDRAW","RESTORE"),restored.events().stream().map(ResourceGovernanceService.PublicationEvent::action).toList());
        assertEquals(before,jdbc.queryForMap("SELECT published_at,file_storage_key,status FROM teaching_resource WHERE id=?",id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE resource_id=?",Integer.class,id));
        error(409,()->governance.change(admin,id,false,null));
    }
    @Test void withdrawalFiltersEveryConsumerReadModelAndRestoresFavorites(){
        published.favorite(student,id,true);published.detail(student,id);
        var stats=statistics.overview(admin);withdraw();
        error(404,()->published.detail(student,id));error(404,()->published.attachment(student,id,true));
        error(404,()->published.favorite(student,id,true));error(404,()->supplements.metadata(student,id,"public"));
        error(404,()->portal.presentation(student,id));
        assertEquals(0,published.list(student,tag,null,null,null,false,1,10).total());
        assertEquals(0,published.list(student,tag,null,null,null,true,1,10).total());
        assertFalse(portal.history(student,"browse",1,100).items().stream().anyMatch(r->r.resourceId()==id));
        assertEquals(0,portal.navigation(student,false).stream().filter(r->r.id()==course).findFirst().orElseThrow().resourceCount());
        assertEquals(0,portal.navigation(student,true).stream().filter(r->r.id()==element).findFirst().orElseThrow().resourceCount());
        var after=statistics.overview(admin);assertEquals(stats.resources().approved()-1,after.resources().approved());assertEquals(stats.resources().withdrawn()+1,after.resources().withdrawn());
        assertEquals(stats.usage().browseEvents(),after.usage().browseEvents());assertEquals(stats.usage().currentFavorites()-1,after.usage().currentFavorites());
        assertEquals(1,portal.ledger(admin,tag,null,null,null,1,10,"WITHDRAWN").total());assertEquals(0,portal.ledger(admin,tag,null,null,null,1,10,"LIVE").total());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE resource_id=? AND active=TRUE",Integer.class,id));
        governance.change(admin,id,false,null);assertEquals(1,published.list(student,tag,null,null,null,true,1,10).total());
    }
    @Test void feedbackIsPrivateDeduplicatedAndSurvivesWithdrawal(){
        governance.report(student,id,new ResourceGovernanceService.FeedbackRequest("RIGHTS","请核对来源"));
        error(409,()->governance.report(student,id,new ResourceGovernanceService.FeedbackRequest("FILE","重复")));
        assertEquals(0,governance.feedback(teacher,false,"ALL",id,1,10).total());
        var own=governance.feedback(student,false,"ALL",id,1,10);long feedback=own.items().getFirst().id();
        withdraw();assertEquals(1,governance.feedback(student,false,"ALL",id,1,10).total());
        error(404,()->governance.report(student,id,new ResourceGovernanceService.FeedbackRequest("FILE","不可用")));
        governance.handle(admin,feedback,new ResourceGovernanceService.HandleRequest("RESOLVED","已暂停发布并核查"));
        var handled=governance.feedback(student,false,"RESOLVED",id,1,10).items().getFirst();assertNotNull(handled.handledAt());assertEquals("已暂停发布并核查",handled.response());
        error(409,()->governance.handle(admin,feedback,new ResourceGovernanceService.HandleRequest("DISMISSED","覆盖")));
    }
    @Test void invalidRequestsAndRolesDoNotMutateRecords(){
        error(403,()->governance.change(teacher,id,true,new ResourceGovernanceService.PublicationRequest("越权")));
        error(400,()->governance.change(admin,id,true,new ResourceGovernanceService.PublicationRequest(" ")));
        error(400,()->governance.report(student,id,new ResourceGovernanceService.FeedbackRequest("UNKNOWN","说明")));
        error(400,()->governance.report(student,id,new ResourceGovernanceService.FeedbackRequest("FILE"," ")));
        error(400,()->governance.report(student,id,new ResourceGovernanceService.FeedbackRequest("FILE","x".repeat(1001))));
        error(403,()->governance.feedback(student,true,"ALL",null,1,10));
        error(400,()->governance.feedback(student,false,"OTHER",null,1,10));
        error(400,()->governance.handle(admin,1,new ResourceGovernanceService.HandleRequest("OPEN","说明")));
        assertNull(governance.publication(admin,id).withdrawnAt());assertTrue(governance.publication(admin,id).events().isEmpty());
        assertEquals(0,governance.feedback(student,false,"ALL",id,1,10).total());
    }
    @Test void draftCannotBePublishedThroughGovernanceAndApprovedMetadataStaysImmutable(){
        error(409,()->supplements.save(teacher,id,new ResourceSupplementService.MetadataRequest("改作者",null,null)));
        withdraw();error(409,()->supplements.save(teacher,id,new ResourceSupplementService.MetadataRequest("改作者",null,null)));
        governance.change(admin,id,false,null);
        jdbc.update("DELETE FROM audit_record WHERE resource_id=?",id);
        jdbc.update("UPDATE teaching_resource SET status='DRAFT',submission_no=0,published_at=NULL WHERE id=?",id);
        error(404,()->governance.publication(admin,id));error(404,()->governance.change(admin,id,true,new ResourceGovernanceService.PublicationRequest("草稿")));
    }
}
