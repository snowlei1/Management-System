package cn.edu.jxnu.civicsresources.statistics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.*;

class StatisticsServiceTest {
    final StatisticsRepository repository = mock(StatisticsRepository.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final StatisticsService service = new StatisticsService(repository, manager);
    @Test void anonymousDeniedBeforeQuery() {
        assertEquals(401,assertThrows(BusinessException.class,()->service.overview(null)).status().value());
        verifyNoInteractions(repository,manager);
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","STUDENT"}) void otherRolesDeniedBeforeQuery(String role) {
        assertEquals(403,assertThrows(BusinessException.class,()->service.overview(user(role))).status().value());
        verifyNoInteractions(repository,manager);
    }
    @Test void adminUsesOneReadOnlyRepeatableReadAndFixedQueries() {
        TransactionStatus status = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenAnswer(i -> {
            TransactionDefinition d=i.getArgument(0);
            assertTrue(d.isReadOnly());assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,d.getIsolationLevel());
            return status;
        });
        when(repository.courseDistribution()).thenReturn(List.of());
        when(repository.categoryDistribution()).thenReturn(List.of());
        when(repository.elementDistribution()).thenReturn(List.of());
        assertNotNull(service.overview(user("ADMIN")));
        verify(manager,times(1)).commit(status);
        verify(repository).users();verify(repository).courses();verify(repository).elements();verify(repository).categories();
        verify(repository).resources();verify(repository).usage();verify(repository).courseDistribution();
        verify(repository).categoryDistribution();verify(repository).elementDistribution();verifyNoMoreInteractions(repository);
    }
    @Test void queryFailureDoesNotReturnPartialStatistics() {
        TransactionStatus status=mock(TransactionStatus.class);when(manager.getTransaction(any())).thenReturn(status);
        when(repository.users()).thenThrow(new IllegalStateException("controlled query failure"));
        assertThrows(IllegalStateException.class,()->service.overview(user("ADMIN")));verify(manager).rollback(status);verify(manager,never()).commit(any());
    }
    static UserPrincipal user(String role) { return new UserPrincipal(1,"test","unused",role,"ACTIVE"); }
}
