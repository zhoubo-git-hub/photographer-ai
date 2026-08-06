package com.photogai.modules.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退订请求（关闭自动续费）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCancelRequest {
    private String reason;
}
