package com.photogai.modules.dashboard;

import com.photogai.modules.auth.UserRepository;
import com.photogai.modules.auth.entity.User;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.StatusHistoryRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import com.photogai.modules.dashboard.dto.FunnelDTO;
import com.photogai.modules.dashboard.dto.MemberPerfDTO;
import com.photogai.modules.dashboard.dto.OverviewDTO;
import com.photogai.modules.dashboard.dto.RevenuePointDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 经营看板服务：收入 / 转化 / 复购 / 客单价 + 漏斗 + 成员业绩。
 *
 * <p>纯聚合 {@code orders}/{@code status_history}/{@code customer}，<b>不新增埋点表</b>。
 * 多租户隔离：所有查询按 {@code studio_id}。成员维度按 {@code orders.assigned_to}。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<OrderStatus> FUNNEL_ORDER = List.of(
            OrderStatus.CONSULT, OrderStatus.DEPOSIT, OrderStatus.SHOOT,
            OrderStatus.EDIT, OrderStatus.DELIVER);
    /** 计入"成交收入"的状态。 */
    private static final List<OrderStatus> DEAL_STATUSES = List.of(
            OrderStatus.DELIVER, OrderStatus.REPURCHASE);

    private final SubscriptionService subscriptionService;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;

    /** C1 概览。 */
    @Transactional(readOnly = true)
    public OverviewDTO overview(Long studioId, LocalDateTime from, LocalDateTime to) {
        subscriptionService.requirePro(studioId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = from != null ? from : now.minusDays(30);
        LocalDateTime end = to != null ? to : now;

        List<Order> range = orderRepository
                .findByStudioIdAndDeletedAtIsNullAndCreatedAtBetween(studioId, start, end);
        List<Order> deals = range.stream()
                .filter(o -> DEAL_STATUSES.contains(o.getStatus()))
                .collect(Collectors.toList());

        BigDecimal revenue = sumAmount(deals);
        int orderCount = deals.size();
        BigDecimal aov = orderCount == 0 ? ZERO
                : revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        // 复购率：≥2 单客户 / 总客户（基于全量订单，不限于时间窗）
        List<Order> all = orderRepository.findByStudioIdAndDeletedAtIsNull(studioId);
        Map<Long, Long> perCustomer = all.stream()
                .collect(Collectors.groupingBy(Order::getCustomerId, Collectors.counting()));
        long totalCustomers = customerRepository.findByStudioIdAndDeletedAtIsNull(studioId).size();
        long repeat = perCustomer.values().stream().filter(c -> c >= 2).count();
        double repurchaseRate = totalCustomers == 0 ? 0.0 : (double) repeat / totalCustomers;

        Map<OrderStatus, Integer> reached = reachedCounts(studioId);
        OverviewDTO.Conversion conversion = OverviewDTO.Conversion.builder()
                .consult(reached.getOrDefault(OrderStatus.CONSULT, 0))
                .deposit(reached.getOrDefault(OrderStatus.DEPOSIT, 0))
                .shoot(reached.getOrDefault(OrderStatus.SHOOT, 0))
                .deliver(reached.getOrDefault(OrderStatus.DELIVER, 0))
                .build();

        List<RevenuePointDTO> points = deals.stream()
                .filter(o -> o.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        o -> o.getCreatedAt().format(MONTH_FMT),
                        Collectors.reducing(ZERO, DashboardService::amountOrZero, BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> RevenuePointDTO.builder().period(e.getKey()).revenue(e.getValue()).build())
                .collect(Collectors.toList());

        return OverviewDTO.builder()
                .revenue(revenue)
                .orderCount(orderCount)
                .aov(aov)
                .repurchaseRate(repurchaseRate)
                .conversion(conversion)
                .revenuePoints(points)
                .build();
    }

    /** C2 漏斗。 */
    @Transactional(readOnly = true)
    public FunnelDTO funnel(Long studioId, LocalDateTime from, LocalDateTime to) {
        subscriptionService.requirePro(studioId);
        Map<OrderStatus, Integer> reached = reachedCounts(studioId);
        int consult = reached.getOrDefault(OrderStatus.CONSULT, 0);
        List<FunnelDTO.Stage> stages = new ArrayList<>();
        for (OrderStatus s : FUNNEL_ORDER) {
            int c = reached.getOrDefault(s, 0);
            double rate = consult == 0 ? 0.0 : (double) c / consult;
            stages.add(FunnelDTO.Stage.builder().status(s.name()).count(c).rate(rate).build());
        }
        return FunnelDTO.builder().stages(stages).build();
    }

    /** C3 成员业绩（团队版）。 */
    @Transactional(readOnly = true)
    public List<MemberPerfDTO> members(Long studioId) {
        subscriptionService.requireTeam(studioId);
        List<MemberPerfDTO> result = new ArrayList<>();
        for (User u : userRepository.findByStudioId(studioId)) {
            List<Order> os = orderRepository
                    .findByStudioIdAndAssignedToAndDeletedAtIsNull(studioId, u.getId());
            int cnt = os.size();
            BigDecimal rev = sumAmount(os);
            BigDecimal aov = cnt == 0 ? ZERO
                    : rev.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP);
            result.add(MemberPerfDTO.builder()
                    .memberId(u.getId()).name(u.getUsername())
                    .orderCount(cnt).revenue(rev).aov(aov).build());
        }
        return result;
    }

    /** 各状态层到达数（来自 status_history 聚合，JOIN orders 做租户隔离）。 */
    private Map<OrderStatus, Integer> reachedCounts(Long studioId) {
        Map<OrderStatus, Integer> map = new HashMap<>();
        for (Object[] row : statusHistoryRepository.countReachedByStudio(studioId)) {
            String status = (String) row[0];
            Number cnt = (Number) row[1];
            try {
                map.put(OrderStatus.valueOf(status), cnt.intValue());
            } catch (IllegalArgumentException ignored) {
                // 未知状态忽略
            }
        }
        return map;
    }

    private BigDecimal sumAmount(List<Order> orders) {
        return orders.stream().map(DashboardService::amountOrZero)
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal amountOrZero(Order o) {
        return o.getAmount() == null ? ZERO : o.getAmount();
    }
}
