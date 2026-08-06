package com.photogai.modules.schedule.dto;

import com.photogai.modules.order.enums.OrderStatus;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 档期日历条目。{@code conflict=true} 表示与同 studio 其他拍摄重叠（红色高亮）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDTO {

    private Long orderId;
    private String title;
    private LocalDate shootDate;
    private LocalDate shootEndDate;
    private OrderStatus status;
    private boolean conflict;
}
