package com.photogai.modules.order;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.dto.ReminderDTO;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.entity.Reminder;
import com.photogai.modules.order.enums.ReminderStatus;
import com.photogai.modules.order.enums.ReminderType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提醒服务：状态流转触发（P0 仅站内；阶段2 支持可配置规则 + 复购）、列表与标记。
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    /** 创建一条站内提醒（无关联客户）。 */
    @Transactional
    public void create(Long studioId, Long orderId, ReminderType type, LocalDateTime dueAt) {
        create(studioId, orderId, null, type, dueAt);
    }

    /** 创建一条站内提醒（可带客户，复购提醒 orderId 为 null）。 */
    @Transactional
    public void create(Long studioId, Long orderId, Long customerId,
                       ReminderType type, LocalDateTime dueAt) {
        Reminder reminder = new Reminder();
        reminder.setStudioId(studioId);
        reminder.setOrderId(orderId);
        reminder.setCustomerId(customerId);
        reminder.setType(type);
        reminder.setDueAt(dueAt);
        reminder.setStatus(ReminderStatus.PENDING);
        reminderRepository.save(reminder);
    }

    @Transactional(readOnly = true)
    public List<ReminderDTO> listByStudioAndStatus(Long studioId, ReminderStatus status) {
        List<Reminder> reminders = status == null
                ? reminderRepository.findByStudioIdAndStatus(studioId, ReminderStatus.PENDING)
                : reminderRepository.findByStudioIdAndStatus(studioId, status);
        return reminders.stream().map(this::toDto).collect(Collectors.toList());
    }

    /** 仅返回已到期（dueAt ≤ now）且 PENDING 的提醒，供通知中心角标。 */
    @Transactional(readOnly = true)
    public List<ReminderDTO> listDueOnly(Long studioId) {
        List<Reminder> reminders =
                reminderRepository.findByStudioIdAndStatus(studioId, ReminderStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        return reminders.stream()
                .filter(r -> r.getDueAt() == null || !r.getDueAt().isAfter(now))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReminderDTO updateStatus(Long studioId, Long id, ReminderStatus status) {
        Reminder reminder = reminderRepository.findByIdAndStudioId(id, studioId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "提醒不存在"));
        reminder.setStatus(status);
        Reminder saved = reminderRepository.save(reminder);
        return toDto(saved);
    }

    /** 统一映射：补齐订单标题与客户名称（任一项可空）。 */
    private ReminderDTO toDto(Reminder r) {
        String orderTitle = null;
        if (r.getOrderId() != null) {
            Order o = orderRepository.findById(r.getOrderId()).orElse(null);
            orderTitle = o == null ? null : o.getTitle();
        }
        String customerName = null;
        if (r.getCustomerId() != null) {
            Customer c = customerRepository.findById(r.getCustomerId()).orElse(null);
            customerName = c == null ? null : c.getName();
        }
        return ReminderDTO.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .customerId(r.getCustomerId())
                .type(r.getType())
                .dueAt(r.getDueAt())
                .status(r.getStatus())
                .orderTitle(orderTitle)
                .customerName(customerName)
                .build();
    }
}
