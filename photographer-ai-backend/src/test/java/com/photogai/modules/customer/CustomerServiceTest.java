package com.photogai.modules.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.customer.dto.CustomerCreateRequest;
import com.photogai.modules.customer.dto.CustomerDTO;
import com.photogai.modules.customer.dto.CustomerUpdateRequest;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 客户库服务单元测试（Mockito，不连 PG）。
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer sampleCustomer(Long id) {
        Customer c = new Customer();
        c.setId(id);
        c.setStudioId(1L);
        c.setName("张三");
        return c;
    }

    @Test
    void createReturnsDto() {
        CustomerCreateRequest req = CustomerCreateRequest.builder().name("张三").phone("139").build();
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(1L);
            return c;
        });

        CustomerDTO dto = customerService.create(1L, req);
        assertEquals(1L, dto.getId());
        assertEquals("张三", dto.getName());
    }

    @Test
    void detailReturnsCustomer() {
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 1L))
                .thenReturn(Optional.of(sampleCustomer(1L)));

        CustomerDTO dto = customerService.detail(1L, 1L);
        assertEquals(1L, dto.getId());
        assertEquals("张三", dto.getName());
    }

    @Test
    void detailThrowsNotFoundWhenAbsent() {
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> customerService.detail(1L, 999L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void deleteSucceedsWhenNoInProgressOrders() {
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(Optional.of(sampleCustomer(1L)));
        when(orderRepository.findByStudioIdAndCustomerIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(List.of());

        customerService.delete(1L, 1L);
    }

    @Test
    void deleteThrowsForbiddenWhenInProgressOrdersExist() {
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(Optional.of(sampleCustomer(1L)));
        Order inProgress = new Order();
        inProgress.setId(5L);
        inProgress.setStatus(OrderStatus.CONSULT);
        when(orderRepository.findByStudioIdAndCustomerIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(List.of(inProgress));

        BizException ex = assertThrows(BizException.class, () -> customerService.delete(1L, 1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // ========================= 本轮新增分支 =========================

    @Test
    void listReturnsEmptyWhenNoCustomers() {
        when(customerRepository.search(eq(1L), eq(""), any(Pageable.class))).thenReturn(Page.empty());

        var pd = customerService.list(1L, null, 0, 10);
        assertTrue(pd.getContent().isEmpty());
        assertEquals(0, pd.getTotalElements());
    }

    @Test
    void listFiltersByKeywordAndAggregatesStats() {
        Customer c = sampleCustomer(1L);
        when(customerRepository.search(eq(1L), eq("张"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c)));
        when(orderRepository.findByStudioIdAndCustomerIdAndDeletedAtIsNull(1L, 1L)).thenReturn(List.of());

        var pd = customerService.list(1L, "张", 0, 10);
        assertEquals(1, pd.getContent().size());
        assertEquals("张三", pd.getContent().get(0).getName());
    }

    @Test
    void createDefaultsRepurchaseEnabledTrueWhenNull() {
        CustomerCreateRequest req = CustomerCreateRequest.builder().name("李四").build();
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(2L);
            return c;
        });
        CustomerDTO dto = customerService.create(1L, req);
        assertEquals(Boolean.TRUE, dto.getRepurchaseEnabled());
    }

    @Test
    void createKeepsExplicitRepurchaseEnabledFalse() {
        CustomerCreateRequest req = CustomerCreateRequest.builder().name("王五").repurchaseEnabled(false).build();
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(3L);
            return c;
        });
        CustomerDTO dto = customerService.create(1L, req);
        assertEquals(Boolean.FALSE, dto.getRepurchaseEnabled());
    }

    @Test
    void updateThrowsNotFoundWhenAbsent() {
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        BizException ex = assertThrows(BizException.class,
                () -> customerService.update(1L, 999L, CustomerUpdateRequest.builder().name("x").build()));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateAppliesProvidedFields() {
        Customer c = sampleCustomer(1L);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 1L)).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        CustomerDTO dto = customerService.update(1L, 1L,
                CustomerUpdateRequest.builder().name("新名").phone("188").build());
        assertEquals("新名", dto.getName());
        assertEquals("188", dto.getPhone());
    }

    // ===================================================================
    // 本轮新增：补齐 withStats 聚合、update 全字段、delete 非进行中、create 全字段
    // ===================================================================

    private Order order(Long id, LocalDate shootDate, BigDecimal amount, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setStudioId(1L);
        o.setCustomerId(1L);
        o.setTitle("订单" + id);
        o.setStatus(status);
        o.setShootDate(shootDate);
        o.setAmount(amount);
        return o;
    }

    /**
     * detail 聚合历史订单：覆盖 withStats 里 shootDate 非空/为空两侧、
     * max 取最近拍摄日、reduce 累加（含 amount 为 null → ZERO 分支），以及 orders 排序映射。
     */
    @Test
    void detailAggregatesStatsAndHistoryOrders() {
        LocalDate today = LocalDate.now();
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 1L))
                .thenReturn(Optional.of(sampleCustomer(1L)));
        Order dated = order(11L, today, new BigDecimal("1000"), OrderStatus.DELIVER);
        Order undated = order(12L, null, null, OrderStatus.CONSULT);
        when(orderRepository.findByStudioIdAndCustomerIdAndDeletedAtIsNull(1L, 1L))
                .thenReturn(List.of(dated, undated));

        CustomerDTO dto = customerService.detail(1L, 1L);

        assertEquals(2, dto.getOrderCount());
        // amount 为 null 的订单按 ZERO 累加，总额仍为 1000
        assertEquals(0, new BigDecimal("1000").compareTo(dto.getLastAmount()));
        assertEquals(today.atStartOfDay(), dto.getLastOrderAt());
        assertEquals(2, dto.getOrders().size());
        // 无拍摄日的订单排在最后（LocalDateTime.MAX 兜底）
        assertEquals(11L, dto.getOrders().get(0).getId());
        assertEquals(12L, dto.getOrders().get(1).getId());
        assertEquals("张三", dto.getOrders().get(0).getCustomerName());
    }

    /** 全部订单均无拍摄日 → max 得到 empty → lastOrderAt 为 null。 */
    @Test
    void detailLeavesLastOrderAtNullWhenNoShootDate() {
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 1L))
                .thenReturn(Optional.of(sampleCustomer(1L)));
        when(orderRepository.findByStudioIdAndCustomerIdAndDeletedAtIsNull(1L, 1L))
                .thenReturn(List.of(order(11L, null, new BigDecimal("500"), OrderStatus.CONSULT)));

        CustomerDTO dto = customerService.detail(1L, 1L);

        assertNull(dto.getLastOrderAt());
        assertEquals(1, dto.getOrderCount());
        assertEquals(0, new BigDecimal("500").compareTo(dto.getLastAmount()));
    }

    /** update 一次性提供全部可选字段 → 覆盖每个 {@code if (req.getX() != null)} 的 true 分支。 */
    @Test
    void updateAppliesAllOptionalFields() {
        Customer c = sampleCustomer(1L);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 1L)).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        LocalDate lastShoot = LocalDate.of(2024, 5, 20);
        LocalDate birthday = LocalDate.of(1995, 1, 2);
        LocalDate anniversary = LocalDate.of(2020, 10, 1);
        CustomerDTO dto = customerService.update(1L, 1L, CustomerUpdateRequest.builder()
                .name("张三改")
                .wechatId("wx_zs")
                .phone("13800000000")
                .tags("VIP,老客")
                .note("偏好自然光")
                .lastShootDate(lastShoot)
                .repurchaseCycleDays(365)
                .birthday(birthday)
                .anniversary(anniversary)
                .repurchaseEnabled(false)
                .sourceChannel("小红书")
                .build());

        assertEquals("张三改", dto.getName());
        assertEquals("wx_zs", dto.getWechatId());
        assertEquals("13800000000", dto.getPhone());
        assertEquals("VIP,老客", dto.getTags());
        assertEquals("偏好自然光", dto.getNote());
        assertEquals(lastShoot, dto.getLastShootDate());
        assertEquals(365, dto.getRepurchaseCycleDays());
        assertEquals(birthday, dto.getBirthday());
        assertEquals(anniversary, dto.getAnniversary());
        assertEquals(Boolean.FALSE, dto.getRepurchaseEnabled());
        assertEquals("小红书", dto.getSourceChannel());
    }

    /** update 传入全空请求 → 覆盖每个 {@code if} 的 false 分支，原字段保持不变。 */
    @Test
    void updateKeepsFieldsWhenAllRequestFieldsNull() {
        Customer c = sampleCustomer(1L);
        c.setPhone("139");
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 1L)).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        CustomerDTO dto = customerService.update(1L, 1L, CustomerUpdateRequest.builder().build());

        assertEquals("张三", dto.getName());
        assertEquals("139", dto.getPhone());
        assertNull(dto.getTags());
        assertNull(dto.getSourceChannel());
    }

    /** 有历史订单但状态均非进行中（DELIVER）→ anyMatch 为 false → 软删除成功。 */
    @Test
    void deleteSucceedsWhenOrdersAreAllFinished() {
        Customer c = sampleCustomer(1L);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 1L)).thenReturn(Optional.of(c));
        when(orderRepository.findByStudioIdAndCustomerIdAndDeletedAtIsNull(1L, 1L))
                .thenReturn(List.of(
                        order(11L, LocalDate.now(), new BigDecimal("100"), OrderStatus.DELIVER),
                        order(12L, null, null, OrderStatus.REPURCHASE)));

        assertDoesNotThrow(() -> customerService.delete(1L, 1L));
        assertNotNull(c.getDeletedAt());
        verify(customerRepository).save(c);
    }

    /** delete 时客户不存在 → NOT_FOUND。 */
    @Test
    void deleteThrowsNotFoundWhenCustomerAbsent() {
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 999L))
                .thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> customerService.delete(1L, 999L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    /** create 携带全部字段 → 覆盖 create 全字段写入 + CustomerDTO.from 映射。 */
    @Test
    void createPersistsAllProvidedFields() {
        LocalDate lastShoot = LocalDate.of(2024, 3, 8);
        LocalDate birthday = LocalDate.of(1990, 7, 7);
        LocalDate anniversary = LocalDate.of(2018, 9, 9);
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(8L);
            return c;
        });

        CustomerDTO dto = customerService.create(1L, CustomerCreateRequest.builder()
                .name("全字段客户")
                .wechatId("wx_all")
                .phone("13611112222")
                .tags("婚纱,复购")
                .note("需提前一周确认")
                .lastShootDate(lastShoot)
                .repurchaseCycleDays(180)
                .birthday(birthday)
                .anniversary(anniversary)
                .sourceChannel("朋友推荐")
                .repurchaseEnabled(true)
                .build());

        assertEquals(8L, dto.getId());
        assertEquals(1L, dto.getStudioId());
        assertEquals("全字段客户", dto.getName());
        assertEquals("wx_all", dto.getWechatId());
        assertEquals("13611112222", dto.getPhone());
        assertEquals("婚纱,复购", dto.getTags());
        assertEquals("需提前一周确认", dto.getNote());
        assertEquals(lastShoot, dto.getLastShootDate());
        assertEquals(180, dto.getRepurchaseCycleDays());
        assertEquals(birthday, dto.getBirthday());
        assertEquals(anniversary, dto.getAnniversary());
        assertEquals("朋友推荐", dto.getSourceChannel());
        assertEquals(Boolean.TRUE, dto.getRepurchaseEnabled());
    }

    /** list 分页元数据回显 + withStats 在列表场景下的聚合。 */
    @Test
    void listReturnsPageMetadataAndAggregatedStats() {
        Customer c = sampleCustomer(1L);
        when(customerRepository.search(eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 10), 1));
        when(orderRepository.findByStudioIdAndCustomerIdAndDeletedAtIsNull(1L, 1L))
                .thenReturn(List.of(order(11L, LocalDate.now(), new BigDecimal("2000"), OrderStatus.DELIVER)));

        var pd = customerService.list(1L, "", 0, 10);

        assertEquals(1, pd.getContent().size());
        assertEquals(1, pd.getTotalElements());
        assertEquals(1, pd.getTotalPages());
        assertEquals(0, pd.getNumber());
        assertEquals(10, pd.getSize());
        assertEquals(1, pd.getContent().get(0).getOrderCount());
        assertEquals(0, new BigDecimal("2000").compareTo(pd.getContent().get(0).getLastAmount()));
    }
}
