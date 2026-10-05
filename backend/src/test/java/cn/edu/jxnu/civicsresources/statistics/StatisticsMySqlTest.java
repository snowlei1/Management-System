package cn.edu.jxnu.civicsresources.statistics;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="STAGE6_MYSQL_TEST",matches="true")
class StatisticsMySqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired StatisticsService service;
    long course,category,e1,e2,teacher,student;String tag;
    final UserPrincipal admin=new UserPrincipal(1,"test","unused","ADMIN","ACTIVE");
    @BeforeEach void setup() {
        tag="S6-TX-"+UUID.randomUUID().toString().substring(0,8);
        teacher=jdbc.queryForObject("SELECT id FROM app_user WHERE username='dev_teacher'",Long.class);
        student=jdbc.queryForObject("SELECT id FROM app_user WHERE username='dev_student'",Long.class);
        jdbc.update("INSERT INTO course(course_code,name) VALUES (?,?)",tag,tag);
        course=jdbc.queryForObject("SELECT id FROM course WHERE course_code=?",Long.class,tag);
        jdbc.update("INSERT INTO resource_category(name) VALUES (?)",tag);category=id("resource_category",tag);
        jdbc.update("INSERT INTO ideological_element(name) VALUES (?)",tag+"a");e1=id("ideological_element",tag+"a");
        jdbc.update("INSERT INTO ideological_element(name) VALUES (?)",tag+"b");e2=id("ideological_element",tag+"b");
    }
    long id(String table,String name) {return jdbc.queryForObject("SELECT id FROM "+table+" WHERE name=?",Long.class,name);}
    long resource(String state,boolean deleted) {
        jdbc.update("""
            INSERT INTO teaching_resource(title,course_id,category_id,created_by,file_storage_key,file_original_name,file_mime_type,
            file_size_bytes,status,published_at,deleted_at) VALUES (?,?,?,?,?,'临时测试.pdf','application/pdf',1,?, ?, ?)
            """,tag,course,category,teacher,UUID.randomUUID()+".pdf",state,"APPROVED".equals(state)?new java.sql.Timestamp(System.currentTimeMillis()):null,
                deleted?new java.sql.Timestamp(System.currentTimeMillis()):null);
        return jdbc.queryForObject("SELECT MAX(id) FROM teaching_resource WHERE title=?",Long.class,tag);
    }
    @Test void stateTotalsExcludeSoftDeletionAndKeepHistoricalUsage() {
        var before=service.overview(admin);
        long published=resource("APPROVED",false),removed=resource("APPROVED",true);
        resource("DRAFT",false);resource("PENDING",false);resource("REJECTED",false);
        for(long id:List.of(published,removed)) {
            jdbc.update("INSERT INTO browse_record(user_id,resource_id) VALUES (?,?)",student,id);
            jdbc.update("INSERT INTO download_record(user_id,resource_id) VALUES (?,?)",student,id);
            jdbc.update("INSERT INTO favorite(user_id,resource_id) VALUES (?,?)",student,id);
        }
        var after=service.overview(admin);
        assertEquals(before.resources().total()+4,after.resources().total());
        assertEquals(before.resources().deleted()+1,after.resources().deleted());
        assertEquals(before.resources().draft()+1,after.resources().draft());assertEquals(before.resources().pending()+1,after.resources().pending());
        assertEquals(before.resources().rejected()+1,after.resources().rejected());assertEquals(before.resources().approved()+1,after.resources().approved());
        assertEquals(before.usage().browseEvents()+2,after.usage().browseEvents());assertEquals(before.usage().downloadRequests()+2,after.usage().downloadRequests());
        assertEquals(before.usage().currentFavorites()+1,after.usage().currentFavorites());
        jdbc.update("UPDATE favorite SET active=FALSE WHERE resource_id=?",published);
        assertEquals(before.usage().currentFavorites(),service.overview(admin).usage().currentFavorites());
    }
    @Test void multiElementDistributionCountsEachRelationOnceAndKeepsInactiveBases() {
        long a=resource("APPROVED",false),b=resource("DRAFT",false),c=resource("APPROVED",true);
        for(long id:List.of(a,b,c))for(long element:List.of(e1,e2))jdbc.update("INSERT INTO resource_element_relation(resource_id,element_id) VALUES (?,?)",id,element);
        jdbc.update("UPDATE course SET status='INACTIVE' WHERE id=?",course);
        jdbc.update("UPDATE ideological_element SET status='INACTIVE' WHERE id=?",e1);
        var result=service.overview(admin);
        var courseRow=result.courseDistribution().stream().filter(r->r.id()==course).findFirst().orElseThrow();
        assertEquals("INACTIVE",courseRow.status());assertEquals(1,courseRow.resourceCount());
        assertEquals(1,result.categoryDistribution().stream().filter(r->r.id()==category).findFirst().orElseThrow().resourceCount());
        assertEquals(2,result.elementDistribution().stream().filter(r->r.id()==e1||r.id()==e2).mapToLong(r->r.resourceCount()).sum());
    }
    @Test void allAggregateValuesMatchIndependentSqlAndZeroBaseRowsAreKept() {
        var result=service.overview(admin);
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class),result.users().total());
        assertEquals(result.users().total(),result.users().admin()+result.users().teacher()+result.users().student());
        assertEquals(result.users().total(),result.users().active()+result.users().disabled());
        assertEquals(result.resources().total(),result.resources().draft()+result.resources().pending()+result.resources().rejected()+result.resources().approved());
        assertEquals(result.resources().approved(),result.courseDistribution().stream().mapToLong(r->r.resourceCount()).sum());
        assertEquals(result.resources().approved(),result.categoryDistribution().stream().mapToLong(r->r.resourceCount()).sum());
        assertEquals(0,result.courseDistribution().stream().filter(r->r.id()==course).findFirst().orElseThrow().resourceCount());
    }
    @Test void emptyDatabaseSnapshotReturnsZerosNotNull() {
        // Connection-scoped temporary tables shadow the project tables; no persistent data is deleted.
        var definitions=new LinkedHashMap<String,String>();
        definitions.put("app_user","id BIGINT,role_id BIGINT,status VARCHAR(16)");
        for(String table:List.of("course","ideological_element","resource_category"))
            definitions.put(table,"id BIGINT,name VARCHAR(120),status VARCHAR(16)");
        definitions.put("teaching_resource","id BIGINT,status VARCHAR(16),deleted_at DATETIME,published_at DATETIME,course_id BIGINT,category_id BIGINT");
        definitions.put("resource_element_relation","resource_id BIGINT,element_id BIGINT");
        definitions.put("favorite","resource_id BIGINT,active BOOLEAN");
        definitions.put("browse_record","id BIGINT");definitions.put("download_record","id BIGINT");
        var created=new ArrayList<String>();
        try {
            for(var entry:definitions.entrySet()){
                jdbc.execute("CREATE TEMPORARY TABLE "+entry.getKey()+" ("+entry.getValue()+")");created.add(entry.getKey());
            }
            var result=service.overview(admin);
            assertEquals(0,result.users().total());assertEquals(0,result.courses().total());assertEquals(0,result.elements().total());assertEquals(0,result.categories().total());
            assertEquals(0,result.resources().total());assertEquals(0,result.resources().approved());assertEquals(0,result.resources().deleted());
            assertEquals(0,result.usage().browseEvents());assertEquals(0,result.usage().downloadRequests());assertEquals(0,result.usage().currentFavorites());
            assertTrue(result.courseDistribution().isEmpty());assertTrue(result.categoryDistribution().isEmpty());assertTrue(result.elementDistribution().isEmpty());
        } finally {for(String table:created.reversed())jdbc.execute("DROP TEMPORARY TABLE "+table);}
    }
}
