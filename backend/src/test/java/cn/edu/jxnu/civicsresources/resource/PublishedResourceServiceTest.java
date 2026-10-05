package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishedResourceServiceTest {
    @Mock PublishedResourceRepository repository;
    @Mock LocalResourceFileStorage files;
    PublishedResourceService service;
    final UserPrincipal student = new UserPrincipal(5,"student","unused","STUDENT","ACTIVE");
    @BeforeEach void setup() { service = new PublishedResourceService(repository, files, new ResourceDraftServiceTest.TestTransactionManager()); }
    TeachingResource resource(String mime) {
        var t=LocalDateTime.of(2026,10,5,16,0);
        return new TeachingResource(10,"标题","简介",1,"课程","ACTIVE",2,"分类","ACTIVE",7,"APPROVED",
                "a1b2c3d4-1111-2222-3333-444455556666.pdf","资料.pdf",mime,32,t,t,"教师",1,t);
    }
    void visible(String mime) {when(repository.lockVisible(10)).thenReturn(true);when(repository.findVisible(10,5)).thenReturn(Optional.of(new PublishedResourceRepository.Row(resource(mime),false)));}
    void code(int status,Runnable action) {assertEquals(status,assertThrows(BusinessException.class,action::run).status().value());}
    @ParameterizedTest @ValueSource(strings={"ADMIN","UNKNOWN"}) void nonConsumerDenied(String role) {
        var u=new UserPrincipal(1,"x","x",role,"ACTIVE");
        code(403,()->service.list(u,null,null,null,null,false,1,10));code(403,()->service.detail(u,10));
        code(403,()->service.attachment(u,10,true));code(403,()->service.favorite(u,10,true));verifyNoInteractions(repository,files);
    }
    @Test void anonymousDenied() {code(401,()->service.detail(null,10));verifyNoInteractions(repository,files);}
    @ParameterizedTest @CsvSource({"0,10","1,0","1,101","-1,10"}) void badPagination(int page,int size) {code(400,()->service.list(student,null,null,null,null,false,page,size));verifyNoInteractions(repository);}
    @ParameterizedTest @ValueSource(longs={0,-1}) void invalidResourceIds(long id) {code(400,()->service.detail(student,id));code(400,()->service.favorite(student,id,true));verifyNoInteractions(repository);}
    @Test void invalidFilters() {
        code(400,()->service.list(student,"x".repeat(201),null,null,null,false,1,10));
        code(400,()->service.list(student,null,0L,null,null,false,1,10));
        code(400,()->service.list(student,null,null,-1L,null,false,1,10));
        code(400,()->service.list(student,null,null,null,0L,false,1,10));verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"DRAFT","PENDING","REJECTED","DELETED","MISSING"}) void invisibleNotFound(String state) {
        code(404,()->service.detail(student,10));code(404,()->service.attachment(student,10,true));code(404,()->service.favorite(student,10,true));verify(repository,never()).browse(anyLong(),anyLong());verifyNoInteractions(files);
    }
    @Test void listAndFavoritesDoNotRecordUsage() {
        service.list(student,"关键词",1L,2L,3L,false,1,10);service.list(student,null,null,null,null,true,2,10);
        verify(repository).page(5,"关键词",1L,2L,3L,false,1,10);verify(repository).page(5,null,null,null,null,true,2,10);
        verify(repository,never()).browse(anyLong(),anyLong());verify(repository,never()).download(anyLong(),anyLong());
    }
    @Test void detailRecordsSessionUserOncePerRequest() {
        visible("application/pdf");service.detail(student,10);service.detail(student,10);
        verify(repository,times(2)).browse(5,10);verify(repository,never()).download(anyLong(),anyLong());
    }
    @Test void headDetailDoesNotRecord() {visible("application/pdf");service.detail(student,10,false);verify(repository,never()).browse(anyLong(),anyLong());}
    @ParameterizedTest @ValueSource(strings={"application/pdf","image/png","image/jpeg"}) void previewDoesNotRecord(String mime) {
        visible(mime);when(files.readVerified(any())).thenReturn(new byte[]{1,2});assertEquals(2,service.attachment(student,10,false).bytes().length);
        verify(repository,never()).browse(anyLong(),anyLong());verify(repository,never()).download(anyLong(),anyLong());
    }
    @Test void officePreviewExplained() {visible("application/msword");code(400,()->service.attachment(student,10,false));verifyNoInteractions(files);}
    @Test void successfulDownloadRecordsAfterValidation() {
        visible("application/pdf");when(files.readVerified(any())).thenReturn(new byte[]{1,2});service.attachment(student,10,true);
        var order=inOrder(files,repository);order.verify(files).readVerified(any());order.verify(repository).download(5,10);
    }
    @Test void invalidFileNotRecorded() {
        visible("application/pdf");when(files.readVerified(any())).thenThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_FILE","missing"));
        code(400,()->service.attachment(student,10,true));verify(repository,never()).download(anyLong(),anyLong());
    }
    @Test void headDownloadNotRecorded() {visible("application/pdf");service.attachment(student,10,true,false);verify(repository,never()).download(anyLong(),anyLong());}
    @ParameterizedTest @ValueSource(booleans={true,false}) void favoriteDoesNotRecordBrowse(boolean active) {
        visible("application/pdf");assertEquals(active,service.favorite(student,10,active).favorite());verify(repository).favorite(5,10,active);
        verify(repository,never()).browse(anyLong(),anyLong());verify(repository,never()).download(anyLong(),anyLong());
    }
    @Test void publicViewHasNoInternalFields() {
        var fields=Arrays.stream(PublishedResourceView.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        assertFalse(fields.contains("storageKey"));assertFalse(fields.contains("fileStorageKey"));assertFalse(fields.contains("auditRecords"));assertFalse(fields.contains("submissionNo"));
    }
}
