package com.photogai.modules.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.auth.UserRepository;
import com.photogai.modules.auth.entity.User;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.dashboard.dto.FunnelDTO;
import com.photogai.modules.dashboard.dto.MemberPerfDTO;
import com.photogai.modules.dashboard.dto.OverviewDTO;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.StatusHistoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 经营看板服务单元测试（Mockito，不连 PG）。
 *
 * <p>覆盖 C1/C2/C3 主路径 + PRO/TEAM 门禁异常路径。
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private StatusHistoryRepository statusHistoryRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DashboardService dashboardService;

    /** C1 概览：无数据时返回全零聚合。 */
    @Test
    void overviewReturnsZeroAggregatesWhenNoData() {
        when(orderRepository.findByStudioIdAndDeletedAtIsNullAndCreatedAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.findByStudioIdAndDeletedAtIsNull(anyLong())).thenReturn(List.of());
        when(customerRepository.findByStudioIdAndDeletedAtIsNull(anyLong())).thenReturn(List.of());
        when(statusHistoryRepository.countReachedByStudio(anyLong())).thenReturn(List.of());

        OverviewDTO dto = dashboardService.overview(1L, null, null);
        assertNotNull(dto);
        assertEquals(0, dto.getOrderCount());
        assertEquals(0.0, dto.getRepurchaseRate(), 0.0001);
        assertNotNull(dto.getConversion());
        assertNotNull(dto.getRevenuePoints());
    }

    /** C1 概览：未订阅 PRO 抛 PRO_REQUIRED(403)。 */
    @Test
    void overviewThrowsProRequiredWhenNotSubscribed() {
        doThrow(new BizException(ErrorCode.PRO_REQUIRED, "pro required"))
                .when(subscriptionService).requirePro(anyLong());

        BizException ex = assertThrows(BizException.class,
                () -> dashboardService.overview(1L, null, null));
        assertEquals(ErrorCode.PRO_REQUIRED.getCode(), ex.getCode());
    }

    /** C2 漏斗：返回 5 个阶段（CONSULT/DEPOSIT/SHOOT/EDIT/DELIVER）。 */
    @Test
    void funnelReturnsStages() {
        when(statusHistoryRepository.countReachedByStudio(anyLong())).thenReturn(List.of());

        FunnelDTO funnel = dashboardService.funnel(1L, null, null);
        assertNotNull(funnel);
        assertEquals(5, funnel.getStages().size());
    }

    /** C3 成员业绩：按 studio 成员聚合订单数。 */
    @Test
    void membersReturnsList() {
        User u = new User();
        u.setId(2L);
        u.setStudioId(1L);
        u.setUsername("alice");
        u.setRole("MEMBER");
        when(userRepository.findByStudioId(anyLong())).thenReturn(List.of(u));
        when(orderRepository.findByStudioIdAndAssignedToAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(List.of());

        List<MemberPerfDTO> members = dashboardService.members(1L);
        assertEquals(1, members.size());
        assertEquals(2L, members.get(0).getMemberId());
        assertEquals("alice", members.get(0).getName());
    }

    /** C3 成员业绩：非团队版抛 TEAM_REQUIRED(403)。 */
    @Test
    void membersThrowsTeamRequiredWhenNotTeam() {
        doThrow(new BizException(ErrorCode.TEAM_REQUIRED, "team required"))
                .when(subscriptionService).requireTeam(anyLong());

        BizException ex = assertThrows(BizException.class, () -> dashboardService.members(1L));
        assertEquals(ErrorCode.TEAM_REQUIRED.getCode(), ex.getCode());
    }
}
