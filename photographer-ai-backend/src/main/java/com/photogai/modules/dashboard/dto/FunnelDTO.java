package com.photogai.modules.dashboard.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转化漏斗：各状态层到达数与相对咨询层的转化率。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelDTO {
    private List<Stage> stages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stage {
        private String status;
        private int count;
        /** 相对咨询层（CONSULT）的转化率 0~1。 */
        private double rate;
    }
}
