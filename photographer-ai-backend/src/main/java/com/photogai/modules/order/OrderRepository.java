package com.photogai.modules.order;

import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 订单仓储。所有查询按 {@code studio_id} 隔离，并过滤软删除。
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByStudioIdAndDeletedAtIsNull(Long studioId, Pageable pageable);

    Page<Order> findByStudioIdAndStatusAndDeletedAtIsNull(
            Long studioId, OrderStatus status, Pageable pageable);

    List<Order> findByStudioIdAndDeletedAtIsNull(Long studioId);

    List<Order> findByStudioIdAndCustomerIdAndDeletedAtIsNull(Long studioId, Long customerId);

    /** 某客户最近一次有拍摄日的订单（复购任务取拍摄类型）。 */
    @Query("""
           SELECT o FROM Order o
           WHERE o.studioId = :studioId AND o.customerId = :customerId AND o.deletedAt IS NULL
             AND o.shootDate IS NOT NULL
           ORDER BY o.shootDate DESC
           """)
    java.util.Optional<Order> findLatestByStudioAndCustomer(
            @Param("studioId") Long studioId, @Param("customerId") Long customerId);

    long countByStudioIdAndDeletedAtIsNull(Long studioId);

    /** 按成员分配查询（数据看板成员维度；可空回退未分配）。 */
    List<Order> findByStudioIdAndAssignedToAndDeletedAtIsNull(Long studioId, Long assignedTo);

    long countByStudioIdAndAssignedToAndDeletedAtIsNull(Long studioId, Long assignedTo);

    /** 看板时间窗：按创建时间过滤（含软删过滤）。 */
    List<Order> findByStudioIdAndDeletedAtIsNullAndCreatedAtBetween(
            Long studioId, LocalDateTime from, LocalDateTime to);

    /** 看板收入：指定状态集合（如 DELIVER/REPURCHASE）且未删除。 */
    List<Order> findByStudioIdAndStatusInAndDeletedAtIsNull(
            Long studioId, java.util.Collection<OrderStatus> statuses);

    /** 同一 studio 内与候选时间段重叠的未删除订单（用于档期冲突硬阻断）。 */
    @Query("""
           SELECT o FROM Order o
           WHERE o.studioId = :studioId AND o.deletedAt IS NULL
             AND (:excludeOrderId IS NULL OR o.id <> :excludeOrderId)
             AND o.shootDate IS NOT NULL
             AND COALESCE(o.shootEndDate, o.shootDate) >= :start
             AND o.shootDate <= COALESCE(:end, :start)
           """)
    List<Order> findConflicts(@Param("studioId") Long studioId,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end,
                              @Param("excludeOrderId") Long excludeOrderId);

    /** 某月（含跨天）有拍摄排期的未删除订单。 */
    @Query("""
           SELECT o FROM Order o
           WHERE o.studioId = :studioId AND o.deletedAt IS NULL
             AND o.shootDate IS NOT NULL
             AND (o.shootDate BETWEEN :monthStart AND :monthEnd
                  OR COALESCE(o.shootEndDate, o.shootDate) BETWEEN :monthStart AND :monthEnd)
           """)
    List<Order> findByStudioAndMonth(@Param("studioId") Long studioId,
                                     @Param("monthStart") LocalDate monthStart,
                                     @Param("monthEnd") LocalDate monthEnd);
}
