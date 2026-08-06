package com.photogai.modules.order.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 档期冲突明细：与候选时间段重叠的已存在订单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictDTO {

    private Long orderId;
    private String title;
    private LocalDate shootDate;
    private LocalDate shootEndDate;
}
