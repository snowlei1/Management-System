package cn.edu.jxnu.civicsresources.basedata;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaseDataService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private final BaseDataRepository repository;

    public BaseDataService(BaseDataRepository repository) { this.repository = repository; }

    public BaseDataPage list(BaseDataType type, String keyword, String status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw badRequest("分页参数不正确");
        if (keyword != null && keyword.length() > 120) throw badRequest("搜索词不能超过120个字符");
        if (status != null && !status.isBlank()) validateStatus(status);
        return repository.findPage(type, keyword, status, page, size);
    }

    public List<BaseDataOption> options(BaseDataType type) { return repository.findActiveOptions(type); }

    @Transactional
    public BaseDataView create(BaseDataType type, BaseDataRequest request) {
        BaseDataRequest normalized = normalize(type, request);
        checkDuplicate(type, normalized, 0);
        try {
            return require(type, repository.insert(type, normalized));
        } catch (DuplicateKeyException exception) {
            throw duplicate(type);
        }
    }

    @Transactional
    public BaseDataView update(BaseDataType type, long id, BaseDataRequest request) {
        require(type, id);
        BaseDataRequest normalized = normalize(type, request);
        checkDuplicate(type, normalized, id);
        try {
            repository.update(type, id, normalized);
        } catch (DuplicateKeyException exception) {
            throw duplicate(type);
        }
        return require(type, id);
    }

    @Transactional
    public BaseDataView setStatus(BaseDataType type, long id, BaseDataStatusRequest request) {
        require(type, id);
        validateStatus(request.status());
        repository.updateStatus(type, id, request.status());
        return require(type, id);
    }

    private BaseDataView require(BaseDataType type, long id) {
        return repository.findById(type, id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", type.label() + "不存在"));
    }

    private void checkDuplicate(BaseDataType type, BaseDataRequest request, long excludedId) {
        String value = type.isCourse() ? request.courseCode() : request.name();
        if (repository.uniqueValueExists(type, value, excludedId)) throw duplicate(type);
    }

    private static BaseDataRequest normalize(BaseDataType type, BaseDataRequest request) {
        String name = request.name() == null ? "" : request.name().strip();
        if (name.isBlank() || name.length() > type.nameLimit()) {
            throw badRequest(type.label() + "名称长度须为1—" + type.nameLimit() + "个字符");
        }
        String courseCode = null;
        if (type.isCourse()) {
            courseCode = request.courseCode() == null ? "" : request.courseCode().strip();
            if (courseCode.isBlank() || courseCode.length() > 40) throw badRequest("课程编号长度须为1—40个字符");
        }
        String description = request.description() == null ? null : request.description().strip();
        if (description != null && description.length() > 1000) throw badRequest("说明不能超过1000个字符");
        if (description != null && description.isBlank()) description = null;
        return new BaseDataRequest(courseCode, name, description);
    }

    private static void validateStatus(String status) {
        if (!STATUSES.contains(status == null ? "" : status)) throw badRequest("状态只允许 ACTIVE 或 INACTIVE");
    }

    private static BusinessException duplicate(BaseDataType type) {
        return new BusinessException(HttpStatus.CONFLICT, "CONFLICT",
                type.isCourse() ? "课程编号已存在" : type.label() + "名称已存在");
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
