package cn.edu.jxnu.civicsresources.basedata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.common.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@ExtendWith(MockitoExtension.class)
class BaseDataServiceTest {
    @Mock BaseDataRepository repository;
    @InjectMocks BaseDataService service;

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void createsNormalizedData(BaseDataType type) {
        BaseDataRequest normalized = request(type);
        when(repository.insert(type, normalized)).thenReturn(1L);
        when(repository.findById(type, 1L)).thenReturn(Optional.of(view(type, "ACTIVE")));
        BaseDataView result = service.create(type, new BaseDataRequest(" C-001 ", " 名称 ", " 说明 "));
        assertEquals(1L, result.id());
        verify(repository).insert(type, normalized);
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void rejectsDuplicateCreationIncludingInactiveEntries(BaseDataType type) {
        when(repository.uniqueValueExists(type, unique(type), 0)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.create(type, request(type)));
        verify(repository, never()).insert(any(), any());
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void translatesConcurrentDatabaseDuplicate(BaseDataType type) {
        when(repository.insert(type, request(type))).thenThrow(new DuplicateKeyException("database detail"));
        BusinessException error = assertStatus(HttpStatus.CONFLICT, () -> service.create(type, request(type)));
        assertFalse(error.getMessage().contains("database detail"));
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void updateExcludesItsOwnIdFromUniquenessCheck(BaseDataType type) {
        when(repository.findById(type, 1L)).thenReturn(Optional.of(view(type, "ACTIVE")));
        service.update(type, 1L, request(type));
        verify(repository).uniqueValueExists(type, unique(type), 1L);
        verify(repository).update(type, 1L, request(type));
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void rejectsDuplicateUpdate(BaseDataType type) {
        when(repository.findById(type, 1L)).thenReturn(Optional.of(view(type, "ACTIVE")));
        when(repository.uniqueValueExists(type, unique(type), 1L)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.update(type, 1L, request(type)));
        verify(repository, never()).update(any(), anyLong(), any());
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void rejectsBlankName(BaseDataType type) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(type, new BaseDataRequest("C", "  ", null)));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void enforcesEachTableNameLength(BaseDataType type) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(type,
                new BaseDataRequest("C", "名".repeat(type.nameLimit() + 1), null)));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void rejectsOverlongDescription(BaseDataType type) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(type,
                new BaseDataRequest("C", "名称", "文".repeat(1001))));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void rejectsInvalidStatus(BaseDataType type) {
        when(repository.findById(type, 1L)).thenReturn(Optional.of(view(type, "ACTIVE")));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.setStatus(type, 1L, new BaseDataStatusRequest("DISABLED")));
        verify(repository, never()).updateStatus(any(), anyLong(), anyString());
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void deactivationUpdatesOnlyStatus(BaseDataType type) {
        when(repository.findById(type, 1L)).thenReturn(Optional.of(view(type, "ACTIVE")), Optional.of(view(type, "INACTIVE")));
        assertEquals("INACTIVE", service.setStatus(type, 1L, new BaseDataStatusRequest("INACTIVE")).status());
        verify(repository).updateStatus(type, 1L, "INACTIVE");
        verify(repository, times(2)).findById(type, 1L);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void unknownIdCannotBeEditedOrDisabled(BaseDataType type) {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.update(type, 1L, request(type)));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.setStatus(type, 1L, new BaseDataStatusRequest("INACTIVE")));
        verify(repository, never()).update(any(), anyLong(), any());
        verify(repository, never()).updateStatus(any(), anyLong(), anyString());
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void validatesPaginationSearchAndStatus(BaseDataType type) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.list(type, null, null, 0, 10));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.list(type, null, null, 1, 101));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.list(type, "x".repeat(121), null, 1, 10));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.list(type, null, "UNKNOWN", 1, 10));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @EnumSource(BaseDataType.class)
    void optionsUsesOnlyActiveQuery(BaseDataType type) {
        when(repository.findActiveOptions(type)).thenReturn(List.of(new BaseDataOption(1, null, "名称")));
        assertEquals(1, service.options(type).size());
        verify(repository).findActiveOptions(type);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "  ", "12345678901234567890123456789012345678901"})
    void courseRequiresValidCode(String code) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(BaseDataType.COURSE, new BaseDataRequest(code, "名称", null)));
    }

    @Test
    void routeCannotSupplyArbitraryTableName() {
        assertStatus(HttpStatus.NOT_FOUND, () -> BaseDataType.fromPath("app_user"));
    }

    @Test
    void unsupportedDeleteReturns405InsteadOfInternalError() {
        var response = new GlobalExceptionHandler().handleUnsupportedMethod(
                new HttpRequestMethodNotSupportedException("DELETE"));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().code());
    }

    private static String unique(BaseDataType type) { return type.isCourse() ? "C-001" : "名称"; }
    private static BaseDataRequest request(BaseDataType type) { return new BaseDataRequest(type.isCourse() ? "C-001" : null, "名称", "说明"); }
    private static BaseDataView view(BaseDataType type, String status) {
        LocalDateTime time = LocalDateTime.of(2026, 10, 5, 10, 0);
        return new BaseDataView(1, type.isCourse() ? "C-001" : null, "名称", "说明", status, time, time);
    }
    private static BusinessException assertStatus(HttpStatus status, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(status, error.status());
        return error;
    }
}
