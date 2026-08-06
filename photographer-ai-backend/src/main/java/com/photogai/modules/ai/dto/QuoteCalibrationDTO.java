package com.photogai.modules.ai.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 报价校准建议视图（含边界/样本提示）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteCalibrationDTO {
    private Long id;
    private String dimensionKey;
    private String dimensionLabel;
    private int sampleCount;
    private BigDecimal currentCoef;
    private BigDecimal suggestedCoef;
    private int offsetPct;
    private boolean withinBoundary;
    /** PENDING | APPLIED | REJECTED */
    private String status;
    /** 边界/样本提示文案（如"样本不足，仅供参考"）。 */
    private String note;
}
