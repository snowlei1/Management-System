package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="STAGE4_MYSQL_TEST",matches="true")
class ResourceReviewMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceDraftService drafts;
    @Autowired ResourceReviewService reviews;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean ResourceDraftRepository resources;
    @MockitoSpyBean AuditRecordRepository audits;
    @LocalServerPort int port;
    long course,category,element; UserPrincipal teacher,admin; String tag;
    @BeforeEach void fixtures() {
        tag="S4-TX-"+UUID.randomUUID().toString().substring(0,8);
        teacher=principal("dev_teacher","TEACHER");admin=principal("dev_admin","ADMIN");
        jdbc.update("INSERT INTO course(course_code,name) VALUES (?,?)",tag,tag);course=jdbc.queryForObject("SELECT id FROM course WHERE course_code=?",Long.class,tag);
        jdbc.update("INSERT INTO resource_category(name) VALUES (?)",tag);category=jdbc.queryForObject("SELECT id FROM resource_category WHERE name=?",Long.class,tag);
        jdbc.update("INSERT INTO ideological_element(name) VALUES (?)",tag);element=jdbc.queryForObject("SELECT id FROM ideological_element WHERE name=?",Long.class,tag);
    }
    UserPrincipal principal(String username,String role) {return new UserPrincipal(jdbc.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,username),username,"unused",role,"ACTIVE");}
    ResourceDraftView draft() {return drafts.create(teacher,new ResourceDraftRequest(tag,"本地事务夹具",course,category,List.of(element)),LocalResourceFileStorageTest.file("tx.pdf","application/pdf",LocalResourceFileStorageTest.pdf()));}
    @ParameterizedTest @CsvSource({"true,audit","false,audit","true,state","false,state"})
    void auditAndStateRollbackTogether(boolean approve,String failurePoint) {
        long id=draft().id();drafts.submit(teacher,id);
        if ("audit".equals(failurePoint)) doAnswer(i->{i.callRealMethod();throw new DataIntegrityViolationException("controlled after audit insert");}).when(audits).insert(eq(id),anyLong(),anyLong(),anyBoolean(),any());
        else doAnswer(i->{i.callRealMethod();throw new DataIntegrityViolationException("controlled after state update");}).when(resources).decide(eq(id),anyLong(),anyBoolean());
        assertThrows(DataIntegrityViolationException.class,()->{if(approve)reviews.approve(admin,id,1);else reviews.reject(admin,id,1,"事务回滚检查");});
        var persisted=drafts.detail(teacher,id);assertEquals("PENDING",persisted.status());assertEquals(1,persisted.submissionNo());assertNull(persisted.publishedAt());assertTrue(persisted.auditRecords().isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_record WHERE resource_id=?",Integer.class,id));
        System.out.println("STAGE4_ROLLBACK="+tag+" id="+id+" approve="+approve+" point="+failurePoint);
    }
    @Test void submissionRoundRollback() {
        long id=draft().id();doAnswer(i->{i.callRealMethod();throw new DataIntegrityViolationException("controlled after submit update");}).when(resources).submit(eq(id),anyString());
        assertThrows(DataIntegrityViolationException.class,()->drafts.submit(teacher,id));
        var persisted=drafts.detail(teacher,id);assertEquals("DRAFT",persisted.status());assertEquals(0,persisted.submissionNo());assertNull(persisted.publishedAt());
    }
    @Test void missingFileReallyBlocksSubmission() throws Exception {
        long id=draft().id();String key=jdbc.queryForObject("SELECT file_storage_key FROM teaching_resource WHERE id=?",String.class,id);
        Path root=Path.of(System.getenv().getOrDefault("RESOURCE_STORAGE_DIR","uploads/resources")).toAbsolutePath().normalize();
        Path file=root.resolve(key),held=root.resolve(key+".stage4-test-held");
        Files.move(file,held);
        try {assertEquals(400,assertThrows(BusinessException.class,()->drafts.submit(teacher,id)).status().value());assertEquals(0,drafts.detail(teacher,id).submissionNo());}
        finally {Files.move(held,file);}
    }
    @Test void rejectedReplacementRetainsHistoryAndResubmits() throws Exception {
        long id=draft().id();drafts.submit(teacher,id);reviews.reject(admin,id,1,"请完善思政案例说明");
        var edited=drafts.update(teacher,id,new ResourceDraftRequest(tag+"-修改",null,course,category,List.of(element)),
                LocalResourceFileStorageTest.file("revised.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",LocalResourceFileStorageTest.docx()));
        assertEquals("REJECTED",edited.status());assertEquals(1,edited.submissionNo());assertEquals(1,edited.auditRecords().size());
        assertEquals(2,drafts.submit(teacher,id).submissionNo());
        assertEquals(409,assertThrows(BusinessException.class,()->reviews.approve(admin,id,1)).status().value());
        assertEquals("PENDING",drafts.detail(teacher,id).status());assertEquals(1,drafts.detail(teacher,id).auditRecords().size());
        var approved=reviews.approve(admin,id,2);
        assertEquals("APPROVED",approved.status());assertNotNull(approved.publishedAt());assertEquals(2,approved.auditRecords().size());
        assertEquals(List.of("REJECT","APPROVE"),approved.auditRecords().stream().map(AuditRecordView::decision).toList());
        assertArrayEquals(LocalResourceFileStorageTest.docx(),reviews.attachment(admin,id).bytes());
    }
    @Test void twoDistinctAdministratorsConcurrentHttpDecision() throws Exception {
        // Local-only account provisioning: reuse a BCrypt hash, never add an admin-creation business API.
        jdbc.update("""
                INSERT INTO app_user(username,password_hash,display_name,role_id,status)
                SELECT 'dev_admin_b',password_hash,'本地并发审核测试管理员',role_id,'ACTIVE' FROM app_user
                WHERE username='dev_admin' AND NOT EXISTS (SELECT 1 FROM app_user WHERE username='dev_admin_b')
                """);
        assertEquals("ADMIN",jdbc.queryForObject("SELECT r.code FROM app_user u JOIN role r ON r.id=u.role_id WHERE username='dev_admin_b'",String.class));
        long id=draft().id();drafts.submit(teacher,id);var a=login("dev_admin");var b=login("dev_admin_b");
        var latch=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<String>> x=pool.submit(()->{latch.await();return decideHttp(a,id,"approve",null);});
            Future<HttpResponse<String>> y=pool.submit(()->{latch.await();return decideHttp(b,id,"reject","并发测试驳回");});
            latch.countDown();var rx=x.get(20,TimeUnit.SECONDS);var ry=y.get(20,TimeUnit.SECONDS);
            assertEquals(List.of(200,409),java.util.stream.Stream.of(rx.statusCode(),ry.statusCode()).sorted().toList());
            var finalRow=drafts.detail(teacher,id);assertEquals(1,finalRow.submissionNo());assertEquals(1,finalRow.auditRecords().size());
            var record=finalRow.auditRecords().getFirst();boolean passed=rx.statusCode()==200;
            assertEquals(passed?"APPROVED":"REJECTED",finalRow.status());assertEquals(passed?"APPROVE":"REJECT",record.decision());
            assertEquals(principal(passed?"dev_admin":"dev_admin_b","ADMIN").id(),record.reviewerId());
            assertEquals(passed,finalRow.publishedAt()!=null);assertEquals("INVALID_RESOURCE_STATE",mapper.readTree(passed?ry.body():rx.body()).get("code").asText());
            System.out.println("STAGE4_HTTP_CONCURRENCY="+tag+" id="+id+" statuses="+rx.statusCode()+","+ry.statusCode()+" auditRows=1");
        }
    }
    record Session(HttpClient client,String csrf,String header) { }
    Session login(String username) throws Exception {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        var csrf=mapper.readTree(client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("data");
        String password=System.getenv().getOrDefault("STAGE4_ADMIN_PASSWORD","AdminDev#2026");
        var request=HttpRequest.newBuilder(uri("/api/auth/login")).header("Content-Type","application/json").header(csrf.get("headerName").asText(),csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("username",username,"password",password)))).build();
        assertEquals(200,client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
        csrf=mapper.readTree(client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("data");
        return new Session(client,csrf.get("token").asText(),csrf.get("headerName").asText());
    }
    URI uri(String path) {return URI.create("http://127.0.0.1:"+port+path);}
    HttpResponse<String> decideHttp(Session s,long id,String action,String reason) throws Exception {
        var builder=HttpRequest.newBuilder(uri("/api/admin/resource-reviews/"+id+"/"+action)).timeout(Duration.ofSeconds(15)).header(s.header(),s.csrf()).header("Content-Type","application/json");
        return s.client().send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(reason==null?Map.of("submissionNo",1):Map.of("submissionNo",1,"reason",reason)))).build(),HttpResponse.BodyHandlers.ofString());
    }
    @AfterEach void retainFixtures() {
        jdbc.update("UPDATE teaching_resource SET deleted_at=CURRENT_TIMESTAMP(3) WHERE course_id=?",course);
        jdbc.update("UPDATE course SET status='INACTIVE' WHERE id=?",course);jdbc.update("UPDATE resource_category SET status='INACTIVE' WHERE id=?",category);jdbc.update("UPDATE ideological_element SET status='INACTIVE' WHERE id=?",element);
        System.out.println("STAGE4_TX_FIXTURE="+tag);
    }
}
