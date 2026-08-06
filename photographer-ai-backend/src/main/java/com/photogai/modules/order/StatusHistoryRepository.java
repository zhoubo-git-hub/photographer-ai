package com.photogai.modules.order;

import com.photogai.modules.order.entity.StatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 状态流转留痕仓储。
 */
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * 看板漏斗：统计到达各状态的不同订单数。
     *
     * <p>status_history 本身不带 studio_id，需 JOIN orders 按 studio 隔离（与看板零埋点一致）。
     * 返回 {@code [toStatus(String), count(Long)]} 行。
     */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT sh.to_status AS status, COUNT(DISTINCT sh.order_id) AS cnt "
                    + "FROM status_history sh "
                    + "JOIN orders o ON sh.order_id = o.id "
                    + "WHERE o.studio_id = :studioId AND o.deleted_at IS NULL "
                    + "GROUP BY sh.to_status",
            nativeQuery = true)
    java.util.List<java.lang.Object[]> countReachedByStudio(
            @org.springframework.data.repository.query.Param("studioId") Long studioId);
}
