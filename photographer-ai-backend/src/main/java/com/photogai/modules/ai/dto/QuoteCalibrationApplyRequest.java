package com.photogai.modules.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 采纳校准建议请求：携带建议记录 ID。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteCalibrationApplyRequest {
    @NotNull(message = "校准建议 ID 不能为空")
    private Long id;
}
