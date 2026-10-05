package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// Opt-in real MySQL integration tests, never required for portable unit-test runs.
@SpringBootTest
@EnabledIfEnvironmentVariable(named="STAGE3_MYSQL_TEST", matches="true")
class ResourceDraftMySqlTest {
    // Match the real project's storage root so retained soft-deleted test records remain traceable.
    static final Path storage = Path.of(System.getenv().getOrDefault("RESOURCE_STORAGE_DIR", "uploads/resources")).toAbsolutePath().normalize();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("app.resource-storage.directory",storage::toString); }
    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceDraftService service;
    @MockitoSpyBean ResourceDraftRepository repository;
    long course,category,element;
    UserPrincipal teacher;
    String tag;
    @BeforeEach void fixtures() {
        tag="S3-TX-"+UUID.randomUUID().toString().substring(0,8);
        long owner=jdbc.queryForObject("SELECT id FROM app_user WHERE username='dev_teacher'",Long.class);
        teacher=new UserPrincipal(owner,"dev_teacher","unused","TEACHER","ACTIVE");
        jdbc.update("INSERT INTO course(course_code,name) VALUES (?,?)",tag,tag);course=jdbc.queryForObject("SELECT id FROM course WHERE course_code=?",Long.class,tag);
        jdbc.update("INSERT INTO resource_category(name) VALUES (?)",tag);category=jdbc.queryForObject("SELECT id FROM resource_category WHERE name=?",Long.class,tag);
        jdbc.update("INSERT INTO ideological_element(name) VALUES (?)",tag);element=jdbc.queryForObject("SELECT id FROM ideological_element WHERE name=?",Long.class,tag);
    }
    ResourceDraftRequest request(String title) { return new ResourceDraftRequest(title,null,course,category,List.of(element)); }
    MockMultipartFile file(String name) { return LocalResourceFileStorageTest.file(name,"application/pdf",LocalResourceFileStorageTest.pdf()); }
    Set<String> keys() throws IOException {
        if (!Files.exists(storage)) return Set.of();
        try (var stream=Files.list(storage)) { return new HashSet<>(stream.map(p->p.getFileName().toString()).toList()); }
    }
    @Test void realCreateRollbackRemovesRowRelationsAndNewFile() throws IOException {
        Set<String> before=keys();
        // Actually insert both resource and association, then inject failure before commit.
        doAnswer(invocation->{invocation.callRealMethod();throw new DataIntegrityViolationException("controlled post-association failure");})
                .when(repository).replaceElements(anyLong(),anyList());
        assertThrows(DataIntegrityViolationException.class,()->service.create(teacher,request(tag),file("create.pdf")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM teaching_resource WHERE title=?",Integer.class,tag));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM resource_element_relation WHERE element_id=?",Integer.class,element));
        assertEquals(before,keys());
    }
    @Test void realReplacementRollbackRestoresOldMetadataRelationsAndFile() throws IOException {
        var draft=service.create(teacher,request(tag),file("old.pdf"));Set<String> before=keys();
        String oldKey=jdbc.queryForObject("SELECT file_storage_key FROM teaching_resource WHERE id=?",String.class,draft.id());
        byte[] oldBytes=Files.readAllBytes(storage.resolve(oldKey));
        doAnswer(invocation->{invocation.callRealMethod();throw new DataIntegrityViolationException("controlled replacement failure");})
                .when(repository).replaceElements(anyLong(),anyList());
        assertThrows(DataIntegrityViolationException.class,()->service.update(teacher,draft.id(),request(tag+"-changed"),file("new.pdf")));
        var persisted=service.detail(teacher,draft.id());assertEquals(tag,persisted.title());assertEquals("old.pdf",persisted.fileOriginalName());
        assertEquals(element,persisted.elements().getFirst().id());assertEquals(before,keys());assertArrayEquals(oldBytes,Files.readAllBytes(storage.resolve(oldKey)));
    }
    @AfterEach void archiveFixtures() {
        // Preserve test records and associations per project soft-delete policy.
        jdbc.update("UPDATE teaching_resource SET deleted_at=CURRENT_TIMESTAMP(3) WHERE course_id=?",course);
        jdbc.update("UPDATE course SET status='INACTIVE' WHERE id=?",course);
        jdbc.update("UPDATE resource_category SET status='INACTIVE' WHERE id=?",category);
        jdbc.update("UPDATE ideological_element SET status='INACTIVE' WHERE id=?",element);
        // Files of successfully committed fixtures are retained; their test-only storage location is recorded below.
        System.out.println("STAGE3_TX_FIXTURE="+tag+" STORAGE="+storage);
    }
}
