package com.photogai.modules.customer.dto;

import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.dto.OrderDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户视图对象。详情接口会附带 {@code orders} 与统计字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {

    private Long id;
    private Long studioId;
    private String name;
    private String wechatId;
    private String phone;
    private String tags;
    private String note;
    private LocalDate lastShootDate;
    private Integer repurchaseCycleDays;
    private LocalDate birthday;
    private LocalDate anniversary;
    private Boolean repurchaseEnabled;
    private String sourceChannel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 历史订单数（聚合统计）。 */
    private Integer orderCount;
    /** 最近一次拍摄日（聚合统计）。 */
    private LocalDateTime lastOrderAt;
    /** 历史订单总额（聚合统计）。 */
    private BigDecimal lastAmount;

    /** 仅详情接口填充：该客户全部订单。 */
    private List<OrderDTO> orders;

    public static CustomerDTO from(Customer customer) {
        if (customer == null) {
            return null;
        }
        return CustomerDTO.builder()
                .id(customer.getId())
                .studioId(customer.getStudioId())
                .name(customer.getName())
                .wechatId(customer.getWechatId())
                .phone(customer.getPhone())
                .tags(customer.getTags())
                .note(customer.getNote())
                .lastShootDate(customer.getLastShootDate())
                .repurchaseCycleDays(customer.getRepurchaseCycleDays())
                .birthday(customer.getBirthday())
                .anniversary(customer.getAnniversary())
                .repurchaseEnabled(customer.getRepurchaseEnabled())
                .sourceChannel(customer.getSourceChannel())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
