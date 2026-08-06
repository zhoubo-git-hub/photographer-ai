package com.photogai.modules.order.dto;

import com.photogai.modules.order.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态流转请求。仅允许相邻状态（见 {@link com.photogai.modules.order.statemachine.OrderStateMachine}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusChangeRequest {

    @NotNull(message = "目标状态不能为空")
    private OrderStatus toStatus;

    /** 操作人（可选，缺省取当前登录用户）。 */
    private Long operatorId;
}
