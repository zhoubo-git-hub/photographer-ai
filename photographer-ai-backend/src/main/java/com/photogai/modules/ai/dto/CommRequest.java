package com.photogai.modules.ai.dto;

import com.photogai.modules.ai.enums.CommScenario;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沟通助手请求：指定场景，可带订单或客户。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommRequest {

    /** 关联订单（催款/跟进等场景）。 */
    private Long orderId;

    /** 关联客户（复购话术场景）。 */
    private Long customerId;

    @NotNull(message = "场景不能为空")
    private CommScenario scenario;
}
