package com.photogai.modules.dashboard.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 成员业绩（看板成员维度，按 assigned_to 拆分）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPerfDTO {
    private Long memberId;
    private String name;
    private int orderCount;
    private BigDecimal revenue;
    private BigDecimal aov;
}
