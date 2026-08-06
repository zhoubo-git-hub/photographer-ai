package com.photogai.modules.repurchase;

import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.ReminderRepository;
import com.photogai.modules.order.ReminderService;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.entity.Reminder;
import com.photogai.modules.order.enums.ReminderStatus;
import com.photogai.modules.order.enums.ReminderType;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.repurchase.dto.RepurchaseTaskDTO;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 复购引擎服务：每日定时扫描 PRO 工作室的复购候选客户，基于画像 {@code lastShootDate}+{@code repurchaseCycleDays}
 * 生成 {@link ReminderType#REPURCHASE} 站内提醒，幂等去重（customer+type+status）。
 *
 * <p>话术由前端调阶段2 已有的 {@code POST /api/ai/comm}（scenario=REPURCHASE）生成，
 * 复用 AI 沟通助手与 LLM 降级策略。本服务只负责候选识别与提醒落盘。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepurchaseService {

    private final StudioRepository studioRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderService reminderService;
    private final QuotaService quotaService;

    /** 复购扫描 cron（每日 02:30），可由环境变量 APP_REPURCHASE_CRON 覆盖。 */
    @Value("${app.repurchase.cron:0 30 2 * * ?}")
    private String cronExpression;

    /**
     * 每日定时扫描所有 PRO 工作室的复购候选，生成 REPURCHASE 站内提醒（幂等）。
     * 每个工作室独立事务，单个失败不影响其余。
     */
    @Scheduled(cron = "${app.repurchase.cron:0 30 2 * * ?}")
    public void scheduledScan() {
        log.info("[复购引擎] 开始每日扫描：cron={}", cronExpression);
        List<Studio> pros = studioRepository.findAllByPlanType("PRO");
        LocalDate today = LocalDate.now();
        int total = 0;
        for (Studio studio : pros) {
            try {
                total += scanStudio(studio.getId(), today);
            } catch (Exception e) {
                log.error("[复购引擎] 工作室 {} 扫描异常：{}", studio.getId(), e.getMessage(), e);
            }
        }
        log.info("[复购引擎] 扫描完成：PRO 工作室 {} 个，新增复购提醒 {} 条", pros.size(), total);
    }

    /**
     * 扫描单个工作室的复购候选（可手动/测试触发）。返回新增提醒数。
     */
    @Transactional
    public int scanStudio(Long studioId, LocalDate today) {
        List<Customer> candidates = customerRepository.findRepurchaseCandidates(studioId, today);
        int created = 0;
        for (Customer c : candidates) {
            boolean exists = reminderRepository.existsByStudioIdAndCustomerIdAndTypeAndStatus(
                    studioId, c.getId(), ReminderType.REPURCHASE, ReminderStatus.PENDING);
            if (exists) {
                continue; // 幂等：已有待办则不重复生成
            }
            LocalDate lastShoot = c.getLastShootDate();
            int cycle = c.getRepurchaseCycleDays() == null ? 365 : c.getRepurchaseCycleDays();
            LocalDateTime dueAt = lastShoot.plusDays(cycle).atStartOfDay();
            reminderService.create(studioId, null, c.getId(), ReminderType.REPURCHASE, dueAt);
            created++;
        }
        if (created > 0) {
            log.info("[复购引擎] 工作室 {} 新增 {} 条复购提醒", studioId, created);
        }
        return created;
    }

    /** 查询当前工作室的复购任务列表（PRO 门禁在 Service 内）。 */
    @Transactional(readOnly = true)
    public List<RepurchaseTaskDTO> listTasks(Long studioId) {
        quotaService.requirePro(studioId);
        List<Reminder> reminders = reminderRepository.findByStudioIdAndTypeAndStatus(
                studioId, ReminderType.REPURCHASE, ReminderStatus.PENDING);
        return reminders.stream().map(this::toDto).collect(Collectors.toList());
    }

    private RepurchaseTaskDTO toDto(Reminder r) {
        Customer c = r.getCustomerId() == null
                ? null : customerRepository.findById(r.getCustomerId()).orElse(null);

        String shootType = null;
        if (c != null) {
            Order latest = orderRepository.findLatestByStudioAndCustomer(r.getStudioId(), c.getId())
                    .orElse(null);
            shootType = latest == null ? null : latest.getShootType();
        }
        String lastShoot = (c == null || c.getLastShootDate() == null)
                ? null : c.getLastShootDate().toString();
        Integer cycle = (c == null || c.getRepurchaseCycleDays() == null)
                ? null : c.getRepurchaseCycleDays();

        return RepurchaseTaskDTO.builder()
                .reminderId(r.getId())
                .customerId(r.getCustomerId())
                .customerName(c == null ? null : c.getName())
                .shootType(shootType)
                .lastShootDate(lastShoot)
                .repurchaseCycleDays(cycle)
                .dueAt(r.getDueAt() == null ? null : r.getDueAt().toString())
                .status(r.getStatus() == null ? null : r.getStatus().name())
                .build();
    }
}
