package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="STAGE5_MYSQL_TEST",matches="true")
class PublishedResourceMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceDraftService drafts;
    @Autowired ResourceReviewService reviews;
    @Autowired PublishedResourceService center;
    @MockitoSpyBean PublishedResourceRepository published;
    long course,category,element; String tag; UserPrincipal teacher,student,admin;
    @BeforeEach void fixtures() {
        tag="S5-TX-"+UUID.randomUUID().toString().substring(0,8);
        teacher=principal("dev_teacher","TEACHER");student=principal("dev_student","STUDENT");admin=principal("dev_admin","ADMIN");
        jdbc.update("INSERT INTO course(course_code,name) VALUES (?,?)",tag,tag);course=jdbc.queryForObject("SELECT id FROM course WHERE course_code=?",Long.class,tag);
        jdbc.update("INSERT INTO resource_category(name) VALUES (?)",tag);category=jdbc.queryForObject("SELECT id FROM resource_category WHERE name=?",Long.class,tag);
        jdbc.update("INSERT INTO ideological_element(name) VALUES (?)",tag);element=jdbc.queryForObject("SELECT id FROM ideological_element WHERE name=?",Long.class,tag);
    }
    UserPrincipal principal(String name,String role) {return new UserPrincipal(jdbc.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,name),name,"unused",role,"ACTIVE");}
    long draft() {return drafts.create(teacher,new ResourceDraftRequest(tag,"简介%_!组合",course,category,List.of(element)),LocalResourceFileStorageTest.file("课程思政资料.pdf","application/pdf",LocalResourceFileStorageTest.pdf())).id();}
    long approve() {long id=draft();drafts.submit(teacher,id);reviews.approve(admin,id,1);return id;}
    int events(String table,long id) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE resource_id=?",Integer.class,id);}
    @Test void combinedQueryStablePaginationAndLiteralSearch() {
        long first=approve(),second=approve();jdbc.update("UPDATE teaching_resource SET published_at='2026-10-05 16:00:00.000' WHERE id IN (?,?)",first,second);
        var p=center.list(student,tag,course,category,element,false,1,1);
        assertEquals(2,p.total());assertEquals(second,p.items().getFirst().id());assertEquals(1,p.items().getFirst().elements().size());
        assertEquals(first,center.list(teacher,tag,course,category,element,false,2,1).items().getFirst().id());
        assertEquals(2,center.list(student,"%_!",course,category,element,false,1,10).total());
        assertEquals(0,center.list(student,"' OR 1=1 --",course,category,element,false,1,10).total());
        assertEquals(0,events("browse_record",first)+events("browse_record",second));
    }
    @Test void usageEventsHaveSessionUserAndNoPreviewDuplication() {
        long id=approve();center.list(student,tag,null,null,null,false,1,10);center.detail(student,id);center.detail(student,id);
        center.attachment(student,id,false);reviews.attachment(admin,id);center.favorite(student,id,true);center.list(student,null,null,null,null,true,1,10);
        assertEquals(2,events("browse_record",id));assertEquals(0,events("download_record",id));
        center.attachment(student,id,true);center.attachment(teacher,id,true);center.detail(teacher,id);
        assertEquals(3,events("browse_record",id));assertEquals(2,events("download_record",id));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM browse_record WHERE resource_id=? AND user_id=?",Integer.class,id,student.id()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM download_record WHERE resource_id=? AND user_id=?",Integer.class,id,teacher.id()));
        center.detail(student,id,false);center.attachment(student,id,true,false);assertEquals(3,events("browse_record",id));assertEquals(2,events("download_record",id));
        System.out.println("STAGE5_USAGE="+tag+" id="+id+" browse=3 download=2");
    }
    @ParameterizedTest @ValueSource(strings={"DRAFT","PENDING","REJECTED","DELETED"}) void actualVisibilityIsolation(String state) {
        long id=draft();if(!"DRAFT".equals(state)){drafts.submit(teacher,id);if("REJECTED".equals(state))reviews.reject(admin,id,1,"测试驳回");if("DELETED".equals(state)){reviews.approve(admin,id,1);center.favorite(student,id,true);jdbc.update("UPDATE teaching_resource SET deleted_at=CURRENT_TIMESTAMP(3) WHERE id=?",id);}}
        assertEquals(0,center.list(student,tag,null,null,null,false,1,10).total());assertEquals(0,center.list(student,null,null,null,null,true,1,10).items().stream().filter(r->r.id()==id).count());
        for(var user:List.of(student,teacher)){
            assertEquals(404,assertThrows(BusinessException.class,()->center.detail(user,id)).status().value());
            assertEquals(404,assertThrows(BusinessException.class,()->center.attachment(user,id,false)).status().value());
            assertEquals(404,assertThrows(BusinessException.class,()->center.attachment(user,id,true)).status().value());
            assertEquals(404,assertThrows(BusinessException.class,()->center.favorite(user,id,true)).status().value());
        }
        assertEquals(0,events("browse_record",id));assertEquals(0,events("download_record",id));
    }
    @Test void concurrentFavoritesRemainOneRelationAndCancellationIdempotent() throws Exception {
        long id=approve();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return center.favorite(student,id,true);});var b=pool.submit(()->{start.await();return center.favorite(student,id,true);});
            start.countDown();assertTrue(a.get(15,TimeUnit.SECONDS).favorite());assertTrue(b.get(15,TimeUnit.SECONDS).favorite());
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE resource_id=? AND user_id=? AND active=1",Integer.class,id,student.id()));
        long favoriteId=jdbc.queryForObject("SELECT id FROM favorite WHERE resource_id=? AND user_id=?",Long.class,id,student.id());
        center.favorite(student,id,false);center.favorite(student,id,false);assertFalse(center.detail(student,id).favorite());
        center.favorite(student,id,true);assertEquals(favoriteId,jdbc.queryForObject("SELECT id FROM favorite WHERE resource_id=? AND user_id=?",Long.class,id,student.id()));
        System.out.println("STAGE5_FAVORITE_CONCURRENCY="+tag+" id="+id+" relationRows=1");
    }
    @Test void missingRealFileDownloadDoesNotRecordEvent() throws Exception {
        long id=approve();String key=jdbc.queryForObject("SELECT file_storage_key FROM teaching_resource WHERE id=?",String.class,id);
        Path root=Path.of(System.getenv().getOrDefault("RESOURCE_STORAGE_DIR","uploads/resources")).toAbsolutePath().normalize();Path file=root.resolve(key),held=root.resolve(key+".stage5-test-held");
        Files.move(file,held);try{assertEquals(400,assertThrows(BusinessException.class,()->center.attachment(student,id,true)).status().value());assertEquals(0,events("download_record",id));}finally{Files.move(held,file);}
    }
    @ParameterizedTest @ValueSource(strings={"browse","download"}) void eventInsertFailureReallyRollsBack(String kind) {
        long id=approve();if("browse".equals(kind))doAnswer(i->{i.callRealMethod();throw new DataIntegrityViolationException("controlled after browse insert");}).when(published).browse(anyLong(),eq(id));
        else doAnswer(i->{i.callRealMethod();throw new DataIntegrityViolationException("controlled after download insert");}).when(published).download(anyLong(),eq(id));
        assertThrows(DataIntegrityViolationException.class,()->{if("browse".equals(kind))center.detail(student,id);else center.attachment(student,id,true);});
        assertEquals(0,events("browse_record",id));assertEquals(0,events("download_record",id));
        assertEquals("APPROVED",jdbc.queryForObject("SELECT status FROM teaching_resource WHERE id=?",String.class,id));
    }
    @AfterEach void retainFixtures() {
        jdbc.update("UPDATE teaching_resource SET deleted_at=CURRENT_TIMESTAMP(3) WHERE course_id=?",course);
        jdbc.update("UPDATE course SET status='INACTIVE' WHERE id=?",course);jdbc.update("UPDATE resource_category SET status='INACTIVE' WHERE id=?",category);jdbc.update("UPDATE ideological_element SET status='INACTIVE' WHERE id=?",element);
        System.out.println("STAGE5_TX_FIXTURE="+tag);
    }
}
