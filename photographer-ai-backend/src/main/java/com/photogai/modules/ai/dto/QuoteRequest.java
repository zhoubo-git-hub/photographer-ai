package com.photogai.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 报价请求。基于规则系数（类型/时长/张数/地区/风格）生成 Prompt。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {

    @NotBlank(message = "拍摄类型不能为空")
    private String shootType;

    private Integer durationHours;
    private Integer photoCount;
    private String region;
    private String style;

    /** 可选：用于生成话术中的称呼。 */
    private String customerName;
}
