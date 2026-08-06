package com.photogai.modules.ai.dto;

import com.photogai.modules.ai.enums.CommScenario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沟通助手响应：生成话术 + 场景 + 是否降级。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommResponse {

    /** 生成的话术正文。 */
    private String text;

    /** 场景（回显）。 */
    private CommScenario scenario;

    /** 是否走规则模板兜底（LLM 不可用时为 true）。 */
    private boolean fallback;
}
