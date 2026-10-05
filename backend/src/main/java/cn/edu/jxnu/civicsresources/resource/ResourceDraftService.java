package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResourceDraftService {
    private final ResourceDraftRepository repository;
    private final LocalResourceFileStorage files;
    private final AuditRecordRepository audits;
    private final TransactionTemplate transaction;

    public ResourceDraftService(ResourceDraftRepository repository, LocalResourceFileStorage files,
            PlatformTransactionManager manager, AuditRecordRepository audits) {
        this.repository = repository;
        this.files = files;
        this.audits = audits;
        transaction = new TransactionTemplate(manager);
    }

    public ResourceDraftPage list(UserPrincipal user, String keyword, Long course, Long category,
            String status, int page, int size) {
        teacher(user);
        if (page < 1 || size < 1 || size > 100) throw badRequest("分页参数不正确");
        if (keyword != null && keyword.length() > 200) throw badRequest("搜索词不能超过200个字符");
        if (status != null && !status.isBlank() && !Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED").contains(status)) throw badRequest("资源状态不正确");
        if ((course != null && course <= 0) || (category != null && category <= 0)) throw badRequest("筛选ID不正确");
        return repository.page(user.id(), keyword, course, category, status, false, page, size);
    }

    public ResourceDraftView detail(UserPrincipal user, long id) {
        teacher(user);
        return transaction.execute(tx -> view(owned(user.id(), id, false)));
    }

    public ResourceDraftView create(UserPrincipal user, ResourceDraftRequest request, MultipartFile file) {
        teacher(user);
        ResourceDraftRequest normalized = normalize(request);
        return transaction.execute(tx -> {
            validateReferences(normalized);
            StoredResourceFile saved = files.save(file);
            compensate(saved.key(), null);
            long id = repository.insert(user.id(), normalized, saved);
            repository.replaceElements(id, normalized.elementIds());
            return view(owned(user.id(), id, false));
        });
    }

    public ResourceDraftView update(UserPrincipal user, long id, ResourceDraftRequest request, MultipartFile file) {
        teacher(user);
        return transaction.execute(tx -> {
            TeachingResource current = editable(user.id(), id);
            ResourceDraftRequest normalized = normalize(request);
            validateReferences(normalized);
            StoredResourceFile saved = new StoredResourceFile(current.storageKey(), current.originalName(), current.mimeType(), current.sizeBytes());
            if (file != null) {
                saved = files.save(file);
                compensate(saved.key(), current.storageKey());
            }
            repository.update(id, normalized, saved);
            repository.replaceElements(id, normalized.elementIds());
            return view(owned(user.id(), id, false));
        });
    }

    public void delete(UserPrincipal user, long id) {
        teacher(user);
        transaction.executeWithoutResult(tx -> {
            editable(user.id(), id);
            repository.softDelete(id);
            // Retain the row, associations and its current file for traceability.
        });
    }

    public ResourceDraftView submit(UserPrincipal user, long id) {
        teacher(user);
        return transaction.execute(tx -> {
            TeachingResource current = editable(user.id(), id);
            var elements = repository.elements(id).stream().map(ResourceElementView::id).toList();
            var normalized = normalize(new ResourceDraftRequest(current.title(), current.description(),
                    current.courseId(), current.categoryId(), elements));
            if (elements.isEmpty()) throw badRequest("提交审核时须至少关联一个有效课程思政元素");
            validateReferences(normalized);
            files.verify(current);
            if (current.submissionNo() >= 4294967295L) throw conflict("提交轮次已达到上限");
            if (repository.submit(id, current.status()) != 1) throw conflict("资源状态已变化，请刷新");
            return view(owned(user.id(), id, false));
        });
    }

    private TeachingResource editable(long owner, long id) {
        TeachingResource r = owned(owner, id, true);
        if (!Set.of("DRAFT", "REJECTED").contains(r.status())) throw conflict("待审核或已通过资源不能编辑、删除或重复提交");
        return r;
    }
    private TeachingResource owned(long owner, long id, boolean lock) {
        if (id <= 0) throw badRequest("资源ID不正确");
        TeachingResource r = repository.findOwned(id, owner, lock).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在或不可访问"));
        return r;
    }
    private ResourceDraftView view(TeachingResource r) { return ResourceDraftView.detail(r, repository.elements(r.id()), audits.history(r.id())); }

    private void validateReferences(ResourceDraftRequest r) {
        if (!repository.activeCourse(r.courseId())) throw badRequest("课程不存在或已停用，请重新选择");
        if (!repository.activeCategory(r.categoryId())) throw badRequest("资源分类不存在或已停用，请重新选择");
        for (long element : r.elementIds()) {
            if (!repository.activeElement(element)) throw badRequest("思政元素不存在或已停用，请重新选择");
        }
    }

    static ResourceDraftRequest normalize(ResourceDraftRequest r) {
        if (r == null) throw badRequest("请填写资源信息");
        String title = r.title() == null ? "" : r.title().strip();
        if (title.isBlank() || title.length() > 200) throw badRequest("资源标题长度须为1—200个字符");
        String description = r.description() == null ? null : r.description().strip();
        if (description != null && description.length() > 2000) throw badRequest("资源简介不能超过2000个字符");
        if (description != null && description.isBlank()) description = null;
        if (r.courseId() == null || r.courseId() <= 0 || r.categoryId() == null || r.categoryId() <= 0) {
            throw badRequest("请选择课程和资源分类");
        }
        if (r.elementIds() != null && (r.elementIds().size() > 1000
                || r.elementIds().stream().anyMatch(id -> id == null || id <= 0))) throw badRequest("思政元素ID不正确");
        List<Long> elements = r.elementIds() == null ? List.of() : new TreeSet<>(r.elementIds()).stream().toList();
        return new ResourceDraftRequest(title, description, r.courseId(), r.categoryId(), elements);
    }

    private void compensate(String newKey, String oldKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) files.discard(newKey);
                // STATUS_UNKNOWN is retained for manual DB/file reconciliation, not blindly deleted.
            }
            @Override public void afterCommit() { if (oldKey != null) files.discard(oldKey); }
        });
    }
    private static void teacher(UserPrincipal user) {
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        if (!"TEACHER".equals(user.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅教师可以管理本人资源草稿");
    }
    private static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, "INVALID_RESOURCE_STATE", message);
    }
}
