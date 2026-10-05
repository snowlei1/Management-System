package cn.edu.jxnu.civicsresources.statistics;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import cn.edu.jxnu.civicsresources.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StatisticsService {
    private final StatisticsRepository repository;
    private final TransactionTemplate snapshot;
    public StatisticsService(StatisticsRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        snapshot = new TransactionTemplate(manager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public StatisticsOverview overview(UserPrincipal user) {
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请先登录");
        if (!"ADMIN".equals(user.role())) throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","仅管理员可以查看统计");
        // Fixed nine aggregate queries in one consistent read snapshot; no per-row queries.
        return snapshot.execute(tx -> new StatisticsOverview(repository.users(),repository.courses(),repository.elements(),
                repository.categories(),repository.resources(),repository.usage(),repository.courseDistribution(),
                repository.categoryDistribution(),repository.elementDistribution()));
    }
}
