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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ResourceWorkflowServiceTest {
    @Mock ResourceDraftRepository resources;
    @Mock AuditRecordRepository audits;
    @Mock LocalResourceFileStorage files;
    ResourceDraftService drafts;
    ResourceReviewService reviews;
    final UserPrincipal teacher = new UserPrincipal(7,"teacher","unused","TEACHER","ACTIVE");
    final UserPrincipal admin = new UserPrincipal(9,"admin","unused","ADMIN","ACTIVE");
    @BeforeEach void setup() {
        var manager = new ResourceDraftServiceTest.TestTransactionManager();
        drafts = new ResourceDraftService(resources,files,manager,audits);
        reviews = new ResourceReviewService(resources,audits,files,manager);
    }
    TeachingResource row(String status, long round) {
        var t=LocalDateTime.of(2026,10,5,12,0);
        return new TeachingResource(10,"标题","简介",1,"课程","ACTIVE",2,"分类","ACTIVE",7,status,
                "a1b2c3d4-1111-2222-3333-444455556666.pdf","a.pdf","application/pdf",32,t,t,"教师",round,"APPROVED".equals(status)?t:null);
    }
    void owned(String status, long round) { when(resources.findOwned(10,7,true)).thenReturn(Optional.of(row(status,round))); }
    void refs() {
        when(resources.elements(10)).thenReturn(List.of(new ResourceElementView(3,"元素","ACTIVE")));
        when(resources.activeCourse(1)).thenReturn(true); when(resources.activeCategory(2)).thenReturn(true); when(resources.activeElement(3)).thenReturn(true);
    }
    void assertCode(HttpStatus code, Runnable action) { assertEquals(code,assertThrows(BusinessException.class,action::run).status()); }
    @ParameterizedTest @ValueSource(strings={"DRAFT","REJECTED"}) void successfulSubmission(String state) {
        long round="DRAFT".equals(state)?0:1;owned(state,round);refs();
        when(resources.submit(10,state)).thenReturn(1);when(resources.findOwned(10,7,false)).thenReturn(Optional.of(row("PENDING",round+1)));
        var result=drafts.submit(teacher,10);assertEquals("PENDING",result.status());assertEquals(round+1,result.submissionNo());assertNotNull(result.pendingSubmittedAt());
        verify(files).verify(row(state,round));verify(resources).submit(10,state);verify(audits,never()).insert(anyLong(),anyLong(),anyLong(),anyBoolean(),any());
    }
    @Test void zeroElementsDenied() {owned("DRAFT",0);assertCode(HttpStatus.BAD_REQUEST,()->drafts.submit(teacher,10));verify(resources,never()).submit(anyLong(),any());verifyNoInteractions(files);}
    @Test void inactiveCourseDenied() {owned("DRAFT",0);when(resources.elements(10)).thenReturn(List.of(new ResourceElementView(3,"元素","ACTIVE")));assertCode(HttpStatus.BAD_REQUEST,()->drafts.submit(teacher,10));verifyNoInteractions(files);}
    @Test void inactiveCategoryDenied() {owned("DRAFT",0);when(resources.elements(10)).thenReturn(List.of(new ResourceElementView(3,"元素","ACTIVE")));when(resources.activeCourse(1)).thenReturn(true);assertCode(HttpStatus.BAD_REQUEST,()->drafts.submit(teacher,10));verifyNoInteractions(files);}
    @Test void inactiveElementDenied() {owned("DRAFT",0);when(resources.elements(10)).thenReturn(List.of(new ResourceElementView(3,"元素","INACTIVE")));when(resources.activeCourse(1)).thenReturn(true);when(resources.activeCategory(2)).thenReturn(true);assertCode(HttpStatus.BAD_REQUEST,()->drafts.submit(teacher,10));verifyNoInteractions(files);}
    @Test void invalidTitleRechecked() {var r=row("DRAFT",0);when(resources.findOwned(10,7,true)).thenReturn(Optional.of(new TeachingResource(r.id()," ",r.description(),r.courseId(),r.courseName(),r.courseStatus(),r.categoryId(),r.categoryName(),r.categoryStatus(),r.createdBy(),r.status(),r.storageKey(),r.originalName(),r.mimeType(),r.sizeBytes(),r.createdAt(),r.updatedAt(),r.teacherName(),0,null)));assertCode(HttpStatus.BAD_REQUEST,()->drafts.submit(teacher,10));}
    @Test void missingFileDoesNotAdvanceRound() {owned("DRAFT",0);refs();doThrow(new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_FILE","missing")).when(files).verify(any());assertCode(HttpStatus.BAD_REQUEST,()->drafts.submit(teacher,10));verify(resources,never()).submit(anyLong(),any());}
    @ParameterizedTest @ValueSource(strings={"PENDING","APPROVED"}) void cannotSubmitFrozenState(String state) {owned(state,1);assertCode(HttpStatus.CONFLICT,()->drafts.submit(teacher,10));verifyNoInteractions(files,audits);}
    @ParameterizedTest @ValueSource(strings={"ADMIN","STUDENT"}) void submitRoleDenied(String role) {assertCode(HttpStatus.FORBIDDEN,()->drafts.submit(new UserPrincipal(1,"x","x",role,"ACTIVE"),10));verifyNoInteractions(resources);}
    @Test void submitMissingOwner() {assertCode(HttpStatus.NOT_FOUND,()->drafts.submit(teacher,10));}
    @Test void incrementConflict() {owned("DRAFT",0);refs();assertCode(HttpStatus.CONFLICT,()->drafts.submit(teacher,10));}
    @ParameterizedTest @ValueSource(strings={"DRAFT","REJECTED","APPROVED"}) void onlyPendingCanBeDecided(String state) {
        when(resources.findForReview(10,true)).thenReturn(Optional.of(row(state,1)));
        assertCode(HttpStatus.CONFLICT,()->reviews.approve(admin,10,1));assertCode(HttpStatus.CONFLICT,()->reviews.reject(admin,10,1,"原因"));verifyNoInteractions(files,audits);
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","STUDENT"}) void reviewRoleDenied(String role) {
        var u=new UserPrincipal(1,"x","x",role,"ACTIVE");assertCode(HttpStatus.FORBIDDEN,()->reviews.list(u,null,null,null,null,null,1,10));
        assertCode(HttpStatus.FORBIDDEN,()->reviews.detail(u,10));assertCode(HttpStatus.FORBIDDEN,()->reviews.attachment(u,10));assertCode(HttpStatus.FORBIDDEN,()->reviews.approve(u,10,1));assertCode(HttpStatus.FORBIDDEN,()->reviews.reject(u,10,1,"原因"));verifyNoInteractions(resources,files,audits);
    }
    @Test void missingSessionsDenied() {assertCode(HttpStatus.UNAUTHORIZED,()->reviews.detail(null,10));assertCode(HttpStatus.UNAUTHORIZED,()->drafts.submit(null,10));}
    @ParameterizedTest @ValueSource(strings={""," ","\t\n","　"}) void blankReasonDenied(String s) {assertCode(HttpStatus.BAD_REQUEST,()->reviews.reject(admin,10,1,s));verifyNoInteractions(resources);}
    @Test void reasonLengthAndNull() {assertCode(HttpStatus.BAD_REQUEST,()->reviews.reject(admin,10,1,null));assertCode(HttpStatus.BAD_REQUEST,()->reviews.reject(admin,10,1,"x".repeat(1001)));}
    @Test void approveWritesRecordWithSessionReviewer() {
        when(resources.findForReview(10,true)).thenReturn(Optional.of(row("PENDING",2)));when(resources.decide(10,2,true)).thenReturn(1);when(resources.findForReview(10,false)).thenReturn(Optional.of(row("APPROVED",2)));
        var result=reviews.approve(admin,10,2);assertEquals(2,result.submissionNo());assertNotNull(result.publishedAt());assertNull(result.pendingSubmittedAt());
        var order=inOrder(audits,resources);order.verify(audits).insert(10,2,9,true,null);order.verify(resources).decide(10,2,true);
    }
    @Test void rejectPreservesRoundAndStripsReason() {
        when(resources.findForReview(10,true)).thenReturn(Optional.of(row("PENDING",1)));when(resources.decide(10,1,false)).thenReturn(1);when(resources.findForReview(10,false)).thenReturn(Optional.of(row("REJECTED",1)));
        assertEquals("REJECTED",reviews.reject(admin,10,1," 原因 ").status());verify(audits).insert(10,1,9,false,"原因");
    }
    @Test void staleRoundDenied() {when(resources.findForReview(10,true)).thenReturn(Optional.of(row("PENDING",2)));assertCode(HttpStatus.CONFLICT,()->reviews.approve(admin,10,1));verifyNoInteractions(audits,files);}
    @Test void invalidRoundDenied() {assertCode(HttpStatus.BAD_REQUEST,()->reviews.approve(admin,10,0));verifyNoInteractions(resources);}
    @Test void unsubmittedDraftHidden() {when(resources.findForReview(10,false)).thenReturn(Optional.of(row("DRAFT",0)));assertCode(HttpStatus.NOT_FOUND,()->reviews.detail(admin,10));verifyNoInteractions(audits);}
    @Test void draftAttachmentHidden() {when(resources.findForReview(10,true)).thenReturn(Optional.of(row("DRAFT",0)));assertCode(HttpStatus.NOT_FOUND,()->reviews.attachment(admin,10));verifyNoInteractions(files);}
    @Test void defaultReviewListPending() {reviews.list(admin,null,null,null,null,null,1,10);verify(resources).page(null,null,null,null,"PENDING",true,1,10);}
    @Test void invalidListParameters() {assertCode(HttpStatus.BAD_REQUEST,()->reviews.list(admin,null,null,null,null,"DRAFT",1,10));assertCode(HttpStatus.BAD_REQUEST,()->reviews.list(admin,null,null,null,0L,null,1,10));assertCode(HttpStatus.BAD_REQUEST,()->reviews.list(admin,null,null,null,null,null,0,10));}
}
