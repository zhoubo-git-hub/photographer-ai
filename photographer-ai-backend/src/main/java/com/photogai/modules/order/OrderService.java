package com.photogai.modules.order;

import com.photogai.common.ErrorCode;
import com.photogai.common.PageData;
import com.photogai.exception.BizException;
import com.photogai.modules.auth.UserRepository;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.dto.ConflictDTO;
import com.photogai.modules.order.dto.OrderDTO;
import com.photogai.modules.order.dto.OrderCreateRequest;
import com.photogai.modules.order.dto.OrderUpdateRequest;
import com.photogai.modules.order.dto.StatusChangeRequest;
import com.photogai.modules.order.dto.StatusHistoryDTO;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.entity.StatusHistory;
import com.photogai.modules.order.enums.OrderStatus;
import com.photogai.modules.order.enums.ReminderType;
import com.photogai.modules.order.statemachine.OrderStateMachine;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.reminder.ReminderRuleService;
import com.photogai.modules.reminder.ReminderTriggerEvent;
import com.photogai.modules.team.RoleGuard;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单服务：CRUD、状态流引擎（相邻流转 + 留痕）、档期冲突硬阻断、状态触发提醒。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ScheduleConflictService scheduleConflictService;
    private final OrderStateMachine orderStateMachine;
    private final QuotaService quotaService;
    private final ReminderService reminderService;
    private final ReminderRuleService reminderRuleService;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageData<OrderDTO> list(Long studioId, OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> result = status == null
                ? orderRepository.findByStudioIdAndDeletedAtIsNull(studioId, pageable)
                : orderRepository.findByStudioIdAndStatusAndDeletedAtIsNull(studioId, status, pageable);

        Map<Long, String> nameMap = customerNameMap(studioId);
        List<OrderDTO> content = result.getContent().stream()
                .map(o -> OrderDTO.from(o, nameMap.get(o.getCustomerId()), null))
                .collect(Collectors.toList());

        return PageData.<OrderDTO>builder()
                .content(content)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .number(result.getNumber())
                .size(result.getSize())
                .build();
    }

    @Transactional(readOnly = true)
    public OrderDTO get(Long studioId, Long id) {
        Order order = requireOwned(studioId, id);
        List<StatusHistoryDTO> history = statusHistoryRepository
                .findByOrderIdOrderByCreatedAtDesc(id).stream()
                .map(StatusHistoryDTO::from)
                .collect(Collectors.toList());
        String name = customerName(studioId, order.getCustomerId());
        return OrderDTO.from(order, name, history);
    }

    @Transactional
    public OrderDTO create(Long studioId, Long operatorId, OrderCreateRequest req) {
        quotaService.ensureWithinLimit(studioId);

        if (req.getShootDate() != null) {
            List<ConflictDTO> conflicts = scheduleConflictService.checkConflict(
                    studioId, req.getShootDate(), req.getShootEndDate(), null);
            if (!conflicts.isEmpty()) {
                throw new BizException(ErrorCode.CONFLICT, "档期冲突：与已存在订单拍摄时间重叠");
            }
        }

        Order order = new Order();
        order.setStudioId(studioId);
        order.setCustomerId(req.getCustomerId());
        order.setTitle(req.getTitle());
        order.setShootType(req.getShootType());
        order.setStatus(req.getStatus() == null ? OrderStatus.CONSULT : req.getStatus());
        order.setAmount(req.getAmount());
        order.setDepositAmount(req.getDepositAmount());
        order.setCurrency(req.getCurrency() == null ? "CNY" : req.getCurrency());
        order.setShootDate(req.getShootDate());
        order.setShootEndDate(req.getShootEndDate());
        order.setDurationHours(req.getDurationHours());
        order.setPhotoCount(req.getPhotoCount());
        order.setRegion(req.getRegion());
        order.setStyle(req.getStyle());
        order.setQuoteSuggestion(req.getQuoteSuggestion());
        order.setCreatedBy(operatorId);

        Order saved = orderRepository.save(order);
        quotaService.recountOrders(studioId);

        String name = customerName(studioId, saved.getCustomerId());
        return OrderDTO.from(saved, name, null);
    }

    @Transactional
    public OrderDTO update(Long studioId, Long id, OrderUpdateRequest req) {
        Order order = requireOwned(studioId, id);

        boolean dateChanged = (req.getShootDate() != null
                && !Objects.equals(req.getShootDate(), order.getShootDate()))
                || (req.getShootEndDate() != null
                && !Objects.equals(req.getShootEndDate(), order.getShootEndDate()));

        if (dateChanged && req.getShootDate() != null) {
            List<ConflictDTO> conflicts = scheduleConflictService.checkConflict(
                    studioId, req.getShootDate(), req.getShootEndDate(), id);
            if (!conflicts.isEmpty()) {
                throw new BizException(ErrorCode.CONFLICT, "档期冲突：与已存在订单拍摄时间重叠");
            }
        }

        if (req.getTitle() != null) {
            order.setTitle(req.getTitle());
        }
        if (req.getShootType() != null) {
            order.setShootType(req.getShootType());
        }
        if (req.getAmount() != null) {
            order.setAmount(req.getAmount());
        }
        if (req.getDepositAmount() != null) {
            order.setDepositAmount(req.getDepositAmount());
        }
        if (req.getCurrency() != null) {
            order.setCurrency(req.getCurrency());
        }
        if (req.getShootDate() != null) {
            order.setShootDate(req.getShootDate());
        }
        if (req.getShootEndDate() != null) {
            order.setShootEndDate(req.getShootEndDate());
        }
        if (req.getDurationHours() != null) {
            order.setDurationHours(req.getDurationHours());
        }
        if (req.getPhotoCount() != null) {
            order.setPhotoCount(req.getPhotoCount());
        }
        if (req.getRegion() != null) {
            order.setRegion(req.getRegion());
        }
        if (req.getStyle() != null) {
            order.setStyle(req.getStyle());
        }
        if (req.getQuoteSuggestion() != null) {
            order.setQuoteSuggestion(req.getQuoteSuggestion());
        }

        Order saved = orderRepository.save(order);
        String name = customerName(studioId, saved.getCustomerId());
        return OrderDTO.from(saved, name, null);
    }

    @Transactional
    public void delete(Long studioId, Long id) {
        Order order = requireOwned(studioId, id);
        order.setDeletedAt(LocalDateTime.now());
        orderRepository.save(order);
        quotaService.recountOrders(studioId);
    }

    /**
     * 分配订单给团队成员（团队协作）。
     *
     * <p>团队版专属：requireTeam + 非只读成员；memberId 为 null 表示回退未分配。
     */
    @Transactional
    public OrderDTO assign(Long studioId, Long orderId, Long memberId,
                           Long operatorId, String operatorRole) {
        subscriptionService.requireTeam(studioId);
        RoleGuard.assertWriteOrder(operatorRole);

        Order order = requireOwned(studioId, orderId);
        if (memberId != null) {
            userRepository.findByStudioIdAndId(studioId, memberId)
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "成员不存在或不属于本工作室"));
        }
        order.setAssignedTo(memberId);
        Order saved = orderRepository.save(order);

        String name = customerName(studioId, saved.getCustomerId());
        return OrderDTO.from(saved, name, null);
    }

    @Transactional
    public OrderDTO changeStatus(Long studioId, Long id, StatusChangeRequest req, Long operatorId) {
        Order order = requireOwned(studioId, id);
        OrderStatus from = order.getStatus();
        OrderStatus to = req.getToStatus();

        if (!orderStateMachine.canTransition(from, to)) {
            throw new BizException(ErrorCode.VALIDATION,
                    "非法的状态流转：" + from + " → " + to + "（仅允许相邻状态）");
        }

        order.setStatus(to);
        Order saved = orderRepository.save(order);

        StatusHistory history = new StatusHistory();
        history.setOrderId(saved.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperatorId(operatorId);
        statusHistoryRepository.save(history);

        triggerReminders(studioId, saved, to);

        List<StatusHistoryDTO> historyDtos = statusHistoryRepository
                .findByOrderIdOrderByCreatedAtDesc(saved.getId()).stream()
                .map(StatusHistoryDTO::from)
                .collect(Collectors.toList());
        String name = customerName(studioId, saved.getCustomerId());
        return OrderDTO.from(saved, name, historyDtos);
    }

    /** 状态到达触发点写入站内提醒（阶段2 改为规则驱动，无规则回退硬编码）。 */
    private void triggerReminders(Long studioId, Order order, OrderStatus to) {
        switch (to) {
            case DEPOSIT -> createFromRule(
                    studioId, order, ReminderTriggerEvent.DEPOSIT, ReminderType.DEPOSIT_DUE, false);
            case SHOOT -> createFromRule(
                    studioId, order, ReminderTriggerEvent.SHOOT, ReminderType.SHOOT_TOMORROW, true);
            case EDIT -> createFromRule(
                    studioId, order, ReminderTriggerEvent.EDIT, ReminderType.EDIT_OVERDUE, false);
            case DELIVER -> {
                backfillCustomerProfile(order);
                createFromRule(
                        studioId, order, ReminderTriggerEvent.DELIVER, ReminderType.DELIVER_REVIEW, false);
            }
            default -> { /* 其他状态暂不触发提醒 */ }
        }
    }

    /**
     * 依据提醒规则（或默认硬编码）生成一条提醒。
     *
     * <p>偏移计算统一走 {@link ReminderRuleService#findOffset}：PRO 有启用规则用规则，
     * 否则回退与阶段1 一致的默认偏移，保证免费版不回归。
     *
     * @param baseShootDate 为 true 时基准时间取拍摄日当天 09:00（用于"拍摄前 N 天"），否则取当前时间。
     */
    private void createFromRule(Long studioId, Order order, ReminderTriggerEvent event,
                                ReminderType type, boolean baseShootDate) {
        LocalDateTime base = (baseShootDate && order.getShootDate() != null)
                ? order.getShootDate().atTime(LocalTime.of(9, 0))
                : LocalDateTime.now();
        int offset = reminderRuleService.findOffset(studioId, event);
        reminderService.create(
                studioId, order.getId(), order.getCustomerId(), type, base.plusDays(offset));
    }

    /**
     * 订单到达 {@code DELIVER} 时回填客户画像：最近拍摄日取较大值；
     * 若客户未设置复购周期，按拍摄类型给默认周期（365 天），供复购引擎使用。
     */
    private void backfillCustomerProfile(Order order) {
        if (order.getShootDate() == null) {
            return;
        }
        customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(
                order.getStudioId(), order.getCustomerId()).ifPresent(customer -> {
            boolean changed = false;
            if (customer.getLastShootDate() == null
                    || customer.getLastShootDate().isBefore(order.getShootDate())) {
                customer.setLastShootDate(order.getShootDate());
                changed = true;
            }
            if (customer.getRepurchaseCycleDays() == null) {
                customer.setRepurchaseCycleDays(defaultCycleDays(order.getShootType()));
                changed = true;
            }
            if (changed) {
                customerRepository.save(customer);
            }
        });
    }

    /** 复购默认周期（天）。当前统一 365 天，后续可按拍摄类型细化。 */
    private int defaultCycleDays(String shootType) {
        return 365;
    }

    private Order requireOwned(Long studioId, Long id) {
        return orderRepository.findById(id)
                .filter(o -> Objects.equals(o.getStudioId(), studioId) && o.getDeletedAt() == null)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "订单不存在"));
    }

    private String customerName(Long studioId, Long customerId) {
        return customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(studioId, customerId)
                .map(Customer::getName)
                .orElse(null);
    }

    private Map<Long, String> customerNameMap(Long studioId) {
        return customerRepository.findByStudioIdAndDeletedAtIsNull(studioId).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
    }
}
