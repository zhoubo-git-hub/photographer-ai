package com.photogai.modules.contract.dto;

import com.photogai.modules.contract.ContractTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同模板视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractTemplateDTO {

    private Long id;
    private Long studioId;
    private String name;
    private String category;
    private String content;
    private boolean builtin;
    private Long createdBy;

    public static ContractTemplateDTO from(ContractTemplate t) {
        if (t == null) {
            return null;
        }
        return ContractTemplateDTO.builder()
                .id(t.getId())
                .studioId(t.getStudioId())
                .name(t.getName())
                .category(t.getCategory())
                .content(t.getContent())
                .builtin(t.isBuiltin())
                .createdBy(t.getCreatedBy())
                .build();
    }
}
