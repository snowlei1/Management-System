package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourcePortalServiceTest {
    @Mock ResourcePortalRepository repository;
    ResourcePortalService service;
    UserPrincipal student = user("STUDENT"), teacher = user("TEACHER"), admin = user("ADMIN");
    static UserPrincipal user(String role) { return new UserPrincipal(5,"test","unused",role,"ACTIVE"); }
    @BeforeEach void setup() { service = new ResourcePortalService(repository); }
    void code(int expected, Runnable action) { assertEquals(expected, assertThrows(BusinessException.class,action::run).status().value()); }
    @Test void anonymousCannotReadAnyPortal() {
        code(401,()->service.navigation(null,false)); code(401,()->service.history(null,"browse",1,10));
        code(401,()->service.dashboard(null)); code(401,()->service.pendingRecent(null));
        code(401,()->service.ledger(null,null,null,null,null,1,10)); verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","UNKNOWN"}) void consumerEndpointsRestrictRoles(String role) {
        var u=user(role); code(403,()->service.navigation(u,true));code(403,()->service.navigationDetail(u,false,1));
        code(403,()->service.presentation(u,1));code(403,()->service.history(u,"browse",1,10));verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","STUDENT","UNKNOWN"}) void ownDashboardRestrictsRoles(String role) {
        code(403,()->service.dashboard(user(role)));code(403,()->service.ownPresentations(user(role),List.of(1L)));verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","STUDENT","UNKNOWN"}) void adminEndpointsRestrictRoles(String role) {
        code(403,()->service.pendingRecent(user(role)));code(403,()->service.ledger(user(role),null,null,null,null,1,10));verifyNoInteractions(repository);
    }
    @ParameterizedTest @CsvSource({"0,10","1,0","1,101","-1,10"}) void invalidPages(int page,int size) {
        code(400,()->service.history(student,"browse",page,size));code(400,()->service.ledger(admin,null,null,null,null,page,size));verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"other","BROWSE",""}) void invalidHistoryKind(String kind) {code(400,()->service.history(student,kind,1,10));verifyNoInteractions(repository);}
    @ParameterizedTest @ValueSource(longs={0,-1}) void invalidIds(long id) {code(400,()->service.navigationDetail(student,false,id));code(400,()->service.presentation(student,id));verifyNoInteractions(repository);}
    @Test void invalidLedgerFilters() {
        code(400,()->service.ledger(admin,"a".repeat(201),null,null,null,1,10));code(400,()->service.ledger(admin,null,0L,null,null,1,10));
        code(400,()->service.ledger(admin,null,null,-1L,null,1,10));code(400,()->service.ledger(admin,null,null,null,0L,1,10));verifyNoInteractions(repository);
    }
    @Test void invalidBatchIds() {
        code(400,()->service.ownPresentations(teacher,null));code(400,()->service.ownPresentations(teacher,Collections.nCopies(101,1L)));
        code(400,()->service.ownPresentations(teacher,List.of(0L)));code(400,()->service.ownPresentations(teacher,Arrays.asList(1L,null)));verifyNoInteractions(repository);
    }
    @Test void ownershipMismatchNotFound() {when(repository.owned(5,List.of(1L,2L))).thenReturn(List.of());code(404,()->service.ownPresentations(teacher,List.of(1L,2L)));verify(repository,never()).rows(any());}
    @Test void unavailableResourceNotFoundBeforeUsage() {code(404,()->service.presentation(student,99));verify(repository).visible(99);verifyNoMoreInteractions(repository);}
    @Test void inactiveNavigationNotFound() {when(repository.navigation(false,null,null)).thenReturn(List.of());code(404,()->service.navigationDetail(student,false,99));verifyNoMoreInteractions(repository);}
    @Test void historyUsesSessionIdentityOnly() {service.history(student,"downloads",2,5);verify(repository).history(5,true,2,5);}
    @Test void detailRelatedQueriesUseSessionUserWithoutEventWrites() {
        when(repository.visible(7)).thenReturn(true);when(repository.usage(List.of(7L))).thenReturn(Map.of(7L,new ResourceReadModels.Usage(3,2,1)));
        var r=service.presentation(student,7);assertEquals(3,r.usage().browseEvents());verify(repository).related(7,5,false);verify(repository).related(7,5,true);
    }
}
