package com.photogai.modules.billing;

import com.photogai.modules.billing.entity.PaymentOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 支付单仓储。按 {@code studio_id} 隔离；{@code out_trade_no} 唯一查询。
 */
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOutTradeNo(String outTradeNo);

    List<PaymentOrder> findByStudioIdAndStatus(Long studioId, String status);
}
