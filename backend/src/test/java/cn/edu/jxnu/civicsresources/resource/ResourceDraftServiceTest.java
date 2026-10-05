package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ResourceDraftServiceTest {
    @Mock ResourceDraftRepository repository;
    @Mock LocalResourceFileStorage files;
    @Mock AuditRecordRepository audits;
    ResourceDraftService service;
    final UserPrincipal teacher = new UserPrincipal(7,"teacher","hash","TEACHER","ACTIVE");
    final MockMultipartFile upload = new MockMultipartFile("file","a.pdf","application/pdf",LocalResourceFileStorageTest.pdf());
    final StoredResourceFile saved = new StoredResourceFile("new-key","a.pdf","application/pdf",30);
    @BeforeEach void setup() { service = new ResourceDraftService(repository,files,new TestTransactionManager(),audits); }
    ResourceDraftRequest request(List<Long> ids) { return new ResourceDraftRequest(" 标题 "," 简介 ",1L,2L,ids); }
    TeachingResource row(String status) { var t=LocalDateTime.of(2026,10,5,12,0); return new TeachingResource(10,"标题","简介",1,"课程","ACTIVE",2,"分类","ACTIVE",7,status,"old-key","old.pdf","application/pdf",30,t,t,"教师",0,null); }
    void references() { when(repository.activeCourse(1)).thenReturn(true); when(repository.activeCategory(2)).thenReturn(true); }
    void owned(boolean lock,String status) { when(repository.findOwned(10,7,lock)).thenReturn(Optional.of(row(status))); }
    @Test void draftWithZeroElementsAndSessionCreator() {
        references(); when(files.save(upload)).thenReturn(saved); when(repository.insert(eq(7L),any(),eq(saved))).thenReturn(10L); owned(false,"DRAFT");
        assertEquals("DRAFT",service.create(teacher,request(List.of()),upload).status()); verify(repository).replaceElements(10,List.of()); verify(files,never()).discard(anyString());
    }
    @Test void deduplicatesElements() { var r=ResourceDraftService.normalize(request(List.of(5L,3L,5L))); assertEquals(List.of(3L,5L),r.elementIds()); assertEquals("标题",r.title()); }
    @Test void nullElementsIsEmpty() { assertEquals(List.of(),ResourceDraftService.normalize(request(null)).elementIds()); }
    @ParameterizedTest @ValueSource(strings={"ADMIN","STUDENT"}) void deniesNonTeacher(String role) {
        var user=new UserPrincipal(9,"other","hash",role,"ACTIVE");
        assertStatus(HttpStatus.FORBIDDEN,()->service.create(user,request(List.of()),upload)); assertStatus(HttpStatus.FORBIDDEN,()->service.detail(user,10));
        assertStatus(HttpStatus.FORBIDDEN,()->service.update(user,10,request(List.of()),upload)); assertStatus(HttpStatus.FORBIDDEN,()->service.delete(user,10)); verifyNoInteractions(repository,files);
    }
    @Test void missingSession() { assertStatus(HttpStatus.UNAUTHORIZED,()->service.detail(null,10)); }
    @Test void ownerIsolation() {
        assertStatus(HttpStatus.NOT_FOUND,()->service.detail(teacher,10)); assertStatus(HttpStatus.NOT_FOUND,()->service.update(teacher,10,request(List.of()),upload));
        assertStatus(HttpStatus.NOT_FOUND,()->service.delete(teacher,10)); verifyNoInteractions(files);
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","APPROVED"}) void frozenStates(String status) {
        owned(false,status); owned(true,status); assertEquals(status,service.detail(teacher,10).status());
        assertStatus(HttpStatus.CONFLICT,()->service.update(teacher,10,request(List.of()),upload)); assertStatus(HttpStatus.CONFLICT,()->service.delete(teacher,10)); verifyNoInteractions(files);
    }
    @Test void rejectedEditRetainsState() {
        references(); owned(true,"REJECTED"); owned(false,"REJECTED");
        assertEquals("REJECTED",service.update(teacher,10,request(List.of()),null).status());
        verify(repository,never()).submit(anyLong(),anyString());
    }
    @Test void invalidCourse() { assertStatus(HttpStatus.BAD_REQUEST,()->service.create(teacher,request(List.of()),upload)); verifyNoInteractions(files); }
    @Test void invalidCategory() { when(repository.activeCourse(1)).thenReturn(true); assertStatus(HttpStatus.BAD_REQUEST,()->service.create(teacher,request(List.of()),upload)); verifyNoInteractions(files); }
    @Test void invalidElement() { references(); assertStatus(HttpStatus.BAD_REQUEST,()->service.create(teacher,request(List.of(3L)),upload)); verifyNoInteractions(files); }
    @Test void createRollbackCleansNewFile() {
        references(); when(files.save(upload)).thenReturn(saved); when(repository.insert(eq(7L),any(),eq(saved))).thenReturn(10L);
        doThrow(new DataIntegrityViolationException("test")).when(repository).replaceElements(10,List.of());
        assertThrows(DataIntegrityViolationException.class,()->service.create(teacher,request(List.of()),upload)); verify(files).discard("new-key");
    }
    @Test void replacementRollbackKeepsOldFile() {
        references(); owned(true,"DRAFT"); when(files.save(upload)).thenReturn(saved); doThrow(new DataIntegrityViolationException("test")).when(repository).replaceElements(10,List.of());
        assertThrows(DataIntegrityViolationException.class,()->service.update(teacher,10,request(List.of()),upload)); verify(files).discard("new-key"); verify(files,never()).discard("old-key");
    }
    @Test void replacementCommitCleansOldFileAfterUpdate() {
        references(); owned(true,"DRAFT"); owned(false,"DRAFT"); when(files.save(upload)).thenReturn(saved); service.update(teacher,10,request(List.of()),upload);
        var order=inOrder(repository,files); order.verify(repository).update(eq(10L),any(),eq(saved)); order.verify(files).discard("old-key"); verify(files,never()).discard("new-key");
    }
    @Test void metadataOnlyRetainsFile() {
        references(); owned(true,"DRAFT"); owned(false,"DRAFT"); service.update(teacher,10,request(List.of()),null); verifyNoInteractions(files);
        verify(repository).update(eq(10L),any(),eq(new StoredResourceFile("old-key","old.pdf","application/pdf",30)));
    }
    @Test void softDeleteRetainsFileAndRelations() { owned(true,"DRAFT"); service.delete(teacher,10); verify(repository).softDelete(10); verify(repository,never()).replaceElements(anyLong(),anyList()); verifyNoInteractions(files); }
    @Test void invalidParameters() {
        assertThrows(BusinessException.class,()->ResourceDraftService.normalize(new ResourceDraftRequest(" ",null,1L,2L,List.of())));
        assertThrows(BusinessException.class,()->ResourceDraftService.normalize(new ResourceDraftRequest("x".repeat(201),null,1L,2L,List.of())));
        assertThrows(BusinessException.class,()->ResourceDraftService.normalize(new ResourceDraftRequest("x","x".repeat(2001),1L,2L,List.of())));
        assertThrows(BusinessException.class,()->ResourceDraftService.normalize(request(Arrays.asList(3L,null)))); assertThrows(BusinessException.class,()->ResourceDraftService.normalize(request(List.of(-1L))));
        assertStatus(HttpStatus.BAD_REQUEST,()->service.list(teacher,null,null,null,null,0,10)); assertStatus(HttpStatus.BAD_REQUEST,()->service.list(teacher,null,null,null,"PENDING_REVIEW",1,10)); verifyNoInteractions(repository,files);
    }
    void assertStatus(HttpStatus status,Runnable action) { assertEquals(status,assertThrows(BusinessException.class,action::run).status()); }
    // Real Spring synchronization callbacks, no external DB needed for these unit tests.
    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction,TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
