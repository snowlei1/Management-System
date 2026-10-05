package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PublishedResourceService {
    private final PublishedResourceRepository resources;
    private final LocalResourceFileStorage files;
    private final TransactionTemplate transaction;
    public PublishedResourceService(PublishedResourceRepository resources, LocalResourceFileStorage files,
            PlatformTransactionManager manager) {
        this.resources = resources; this.files = files; transaction = new TransactionTemplate(manager);
    }
    public PublishedResourcePage list(UserPrincipal user, String keyword, Long course, Long category,
            Long element, boolean favoritesOnly, int page, int size) {
        consumer(user);
        if (page < 1 || size < 1 || size > 100 || (keyword != null && keyword.length() > 200)
                || invalidId(course) || invalidId(category) || invalidId(element)) throw bad("分页或筛选参数不正确");
        return transaction.execute(tx -> resources.page(user.id(), keyword, course, category, element, favoritesOnly, page, size));
    }
    public PublishedResourceView detail(UserPrincipal user, long id) {
        return detail(user, id, true);
    }
    public PublishedResourceView detail(UserPrincipal user, long id, boolean recordEvent) {
        consumer(user);
        return transaction.execute(tx -> {
            var view = resources.view(visible(user.id(), id));
            // One successful detail request is one event; no promise about unique visitors or client receipt.
            if (recordEvent) resources.browse(user.id(), id);
            return view;
        });
    }
    public Attachment attachment(UserPrincipal user, long id, boolean download) {
        return attachment(user, id, download, true);
    }
    public Attachment attachment(UserPrincipal user, long id, boolean download, boolean recordEvent) {
        consumer(user);
        return transaction.execute(tx -> {
            var r = visible(user.id(), id).resource();
            if (!download && !Set.of("application/pdf", "image/png", "image/jpeg").contains(r.mimeType()))
                throw bad("该格式暂不支持浏览器内预览，请下载后查看");
            byte[] bytes = files.readVerified(r);
            if (download && recordEvent) resources.download(user.id(), id);
            return new Attachment(r.originalName(), r.mimeType(), bytes);
        });
    }
    public FavoriteState favorite(UserPrincipal user, long id, boolean active) {
        consumer(user);
        return transaction.execute(tx -> {
            visible(user.id(), id);
            resources.favorite(user.id(), id, active);
            return new FavoriteState(id, active);
        });
    }
    private PublishedResourceRepository.Row visible(long user, long id) {
        if (id <= 0) throw bad("资源ID不正确");
        if (!resources.lockVisible(id)) throw notFound();
        return resources.findVisible(id, user).orElseThrow(PublishedResourceService::notFound);
    }
    private static boolean invalidId(Long id) { return id != null && id <= 0; }
    private static void consumer(UserPrincipal user) {
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        if (!Set.of("TEACHER", "STUDENT").contains(user.role()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅教师和学生可以使用资源中心");
    }
    private static BusinessException bad(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message); }
    private static BusinessException notFound() { return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在或不可访问"); }
    public record FavoriteState(long resourceId, boolean favorite) { }
    public record Attachment(String originalName, String mimeType, byte[] bytes) { }
}
