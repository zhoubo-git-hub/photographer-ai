package com.photogai.modules.order.dto;

import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单视图对象。列表仅含基础字段 + customerName；详情含 {@code history}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long id;
    private Long studioId;
    private Long customerId;
    private String customerName;
    private String title;
    private String shootType;
    private OrderStatus status;
    private BigDecimal amount;
    private BigDecimal depositAmount;
    private String currency;
    private LocalDate shootDate;
    private LocalDate shootEndDate;
    private Integer durationHours;
    private Integer photoCount;
    private String region;
    private String style;
    private String quoteSuggestion;
    private Long createdBy;
    private Long assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<StatusHistoryDTO> history;

    public static OrderDTO from(Order order, String customerName,
                                List<StatusHistoryDTO> history) {
        if (order == null) {
            return null;
        }
        return OrderDTO.builder()
                .id(order.getId())
                .studioId(order.getStudioId())
                .customerId(order.getCustomerId())
                .customerName(customerName)
                .title(order.getTitle())
                .shootType(order.getShootType())
                .status(order.getStatus())
                .amount(order.getAmount())
                .depositAmount(order.getDepositAmount())
                .currency(order.getCurrency())
                .shootDate(order.getShootDate())
                .shootEndDate(order.getShootEndDate())
                .durationHours(order.getDurationHours())
                .photoCount(order.getPhotoCount())
                .region(order.getRegion())
                .style(order.getStyle())
                .quoteSuggestion(order.getQuoteSuggestion())
                .createdBy(order.getCreatedBy())
                .assignedTo(order.getAssignedTo())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .history(history)
                .build();
    }
}
