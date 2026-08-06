package com.photogai.modules.billing.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订阅视图（前端展示当前订阅状态）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionView {
    private String planType;
    private String status;
    private LocalDateTime expiresAt;
    private boolean autoRenew;
}
