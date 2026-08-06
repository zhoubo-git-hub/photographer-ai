package com.photogai.modules.customer;

import com.photogai.modules.customer.entity.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 客户库仓储。所有查询按 {@code studio_id} 隔离，并过滤软删除。
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByStudioIdAndIdAndDeletedAtIsNull(Long studioId, Long id);

    /** 按 studio 隔离查询单客户（沟通助手复用）。 */
    default Optional<Customer> findByIdAndStudio(Long id, Long studioId) {
        return findByStudioIdAndIdAndDeletedAtIsNull(studioId, id);
    }

    List<Customer> findByStudioIdAndDeletedAtIsNull(Long studioId);

    /** 复购引擎：满足"最近拍摄日 + 周期 ≤ 今天"且开启复购的客户。 */
    @org.springframework.data.jpa.repository.Query(value = """
           SELECT * FROM customer c
           WHERE c.studio_id = :studioId AND c.deleted_at IS NULL
             AND c.last_shoot_date IS NOT NULL
             AND COALESCE(c.repurchase_enabled, TRUE) = TRUE
             AND (c.last_shoot_date + COALESCE(c.repurchase_cycle_days, 365)) <= :today
           """, nativeQuery = true)
    java.util.List<Customer> findRepurchaseCandidates(
            @org.springframework.data.repository.query.Param("studioId") Long studioId,
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today);

    @Query("""
           SELECT c FROM Customer c
           WHERE c.studioId = :studioId AND c.deletedAt IS NULL
             AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :kw, '%'))
                  OR LOWER(COALESCE(c.phone, '')) LIKE LOWER(CONCAT('%', :kw, '%'))
                  OR LOWER(COALESCE(c.wechatId, '')) LIKE LOWER(CONCAT('%', :kw, '%')))
           """)
    Page<Customer> search(@Param("studioId") Long studioId,
                          @Param("kw") String keyword,
                          Pageable pageable);
}
