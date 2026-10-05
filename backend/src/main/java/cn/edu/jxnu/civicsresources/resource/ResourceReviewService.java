package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ResourceReviewService {
    private final ResourceDraftRepository resources;
    private final AuditRecordRepository audits;
    private final LocalResourceFileStorage files;
    private final TransactionTemplate transaction;
    public ResourceReviewService(ResourceDraftRepository resources, AuditRecordRepository audits,
            LocalResourceFileStorage files, PlatformTransactionManager manager) {
        this.resources = resources; this.audits = audits; this.files = files;
        transaction = new TransactionTemplate(manager);
    }

    public ResourceDraftPage list(UserPrincipal user, String keyword, Long course, Long category, Long teacher,
            String status, int page, int size) {
        admin(user);
        if (page < 1 || size < 1 || size > 100 || (keyword != null && keyword.length() > 200)) throw bad("分页或搜索词不正确");
        if ((course != null && course <= 0) || (category != null && category <= 0) || (teacher != null && teacher <= 0)) throw bad("筛选ID不正确");
        String filter = status == null || status.isBlank() ? "PENDING" : status;
        if (!Set.of("PENDING", "APPROVED", "REJECTED", "ALL").contains(filter)) throw bad("审核列表状态不正确");
        return resources.page(teacher, keyword, course, category, "ALL".equals(filter) ? null : filter, true, page, size);
    }

    public ResourceDraftView detail(UserPrincipal user, long id) {
        admin(user);
        return transaction.execute(tx -> view(visible(id, false)));
    }
    public ReviewAttachment attachment(UserPrincipal user, long id) {
        admin(user);
        return transaction.execute(tx -> {
            // Copy a bounded, verified file while locked; a rejected-file replacement cannot race this read.
            var r = visible(id, true);
            return new ReviewAttachment(r.originalName(), r.mimeType(), files.readVerified(r));
        });
    }
    public ResourceDraftView approve(UserPrincipal user, long id, long expectedRound) { return decide(user, id, expectedRound, true, null); }
    public ResourceDraftView reject(UserPrincipal user, long id, long expectedRound, String reason) {
        admin(user);
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isBlank() || normalized.length() > 1000) throw bad("驳回原因须为1—1000个非空白字符");
        return decide(user, id, expectedRound, false, normalized);
    }
    private ResourceDraftView decide(UserPrincipal user, long id, long expectedRound, boolean approve, String reason) {
        admin(user);
        if (expectedRound < 1 || expectedRound > 4294967295L) throw bad("请提供有效审核轮次");
        return transaction.execute(tx -> {
            var r = find(id, true);
            if (!"PENDING".equals(r.status()) || r.submissionNo() != expectedRound) throw conflict();
            files.verify(r);
            audits.insert(id, r.submissionNo(), user.id(), approve, reason);
            if (resources.decide(id, r.submissionNo(), approve) != 1) throw conflict();
            return view(find(id, false));
        });
    }
    private TeachingResource visible(long id, boolean lock) {
        var r = find(id, lock);
        if (!Set.of("PENDING", "REJECTED", "APPROVED").contains(r.status()) || r.submissionNo() < 1) throw notFound();
        return r;
    }
    private TeachingResource find(long id, boolean lock) {
        if (id <= 0) throw bad("资源ID不正确");
        return resources.findForReview(id, lock).orElseThrow(ResourceReviewService::notFound);
    }
    private ResourceDraftView view(TeachingResource r) { return ResourceDraftView.detail(r, resources.elements(r.id()), audits.history(r.id())); }
    private static void admin(UserPrincipal user) {
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        if (!"ADMIN".equals(user.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可以审核资源");
    }
    private static BusinessException bad(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message); }
    private static BusinessException notFound() { return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在或不可访问"); }
    private static BusinessException conflict() { return new BusinessException(HttpStatus.CONFLICT, "INVALID_RESOURCE_STATE", "资源已不处于待审核状态，请刷新后重试"); }
    public record ReviewAttachment(String originalName, String mimeType, byte[] bytes) { }
}
