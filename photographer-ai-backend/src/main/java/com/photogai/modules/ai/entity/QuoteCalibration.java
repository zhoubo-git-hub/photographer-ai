package com.photogai.modules.ai.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 自学习报价校准建议（受限版）：仅产出"建议 + 安全边界"，采纳才写回（不自动覆盖线上系数）。
 *
 * <p>{@code dimensionKey} 维度键形如 {@code 上海|婚纱写真} 或 {@code 上海|婚纱写真|轻奢}；
 * 安全边界常量（MAX_OFFSET_PCT=15 / MIN_SAMPLE=20）写在 {@code QuoteCalibrationService}，不入库。
 */
@Entity
@Table(name = "quote_calibration")
@Getter
@Setter
@NoArgsConstructor
public class QuoteCalibration extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    /** 维度键：地区|类型 或 地区|类型|风格。 */
    @Column(name = "dimension_key", nullable = false)
    private String dimensionKey;

    /** 展示名：地区·类型[·风格]。 */
    @Column(name = "dimension_label", nullable = false)
    private String dimensionLabel;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "current_coef", nullable = false)
    private BigDecimal currentCoef = BigDecimal.ONE;

    @Column(name = "suggested_coef", nullable = false)
    private BigDecimal suggestedCoef = BigDecimal.ONE;

    /** 建议在 -15..+15 截断。 */
    @Column(name = "offset_pct", nullable = false)
    private int offsetPct;

    @Column(name = "within_boundary", nullable = false)
    private boolean withinBoundary = true;

    /** PENDING | APPLIED | REJECTED */
    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;
}
