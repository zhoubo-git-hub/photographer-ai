package com.photogai.modules.contract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同生成响应：标题 + 替换后的正文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractGenerateResponse {

    private String title;
    private String content;
}
