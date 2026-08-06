package com.photogai.modules.billing;

import com.photogai.modules.billing.entity.Subscription;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 订阅仓储。所有查询按 {@code studio_id} 隔离。
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** 当前有效订阅：status=ACTIVE 且未过期。 */
    @Query("""
           SELECT s FROM Subscription s
           WHERE s.studioId = :studioId AND s.status = 'ACTIVE'
             AND s.expiresAt > :now
           """)
    Optional<Subscription> findActiveByStudioId(
            @Param("studioId") Long studioId, @Param("now") LocalDateTime now);

    /** 已到期但仍为 ACTIVE 的订阅（定时任务降级用）。 */
    @Query("""
           SELECT s FROM Subscription s
           WHERE s.status = 'ACTIVE' AND s.expiresAt <= :now
           """)
    List<Subscription> findDue(@Param("now") LocalDateTime now);

    /** 同工作室最近一条订阅（用于查询当前订阅视图）。 */
    List<Subscription> findByStudioIdOrderByStartedAtDesc(Long studioId);
}
