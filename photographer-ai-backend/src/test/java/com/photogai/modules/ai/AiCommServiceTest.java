package com.photogai.modules.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.ai.dto.CommRequest;
import com.photogai.modules.ai.dto.CommResponse;
import com.photogai.modules.ai.enums.CommScenario;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.quota.QuotaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AI 沟通助手服务单元测试（对应 PRD US-P2-01/02/03/10 / 架构 §7 复用 LlmClient + 降级）。
 *
 * <p>验证：
 * 1. LLM 不可用（密钥缺失 / 调用异常）→ 降级规则模板，fallback=true，话术非空且不抛异常；
 * 2. 复购话术复用同一端点（scenario=REPURCHASE）；
 * 3. requirePro 拦截：FREE 工作室调用抛 PRO_REQUIRED(403 语义)。
 */
@ExtendWith(MockitoExtension.class)
class AiCommServiceTest {

    @Mock
    private QuotaService quotaService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private LlmClient llmClient;

    @InjectMocks
    private AiCommService service;

    @Test
    void generateFallsBackToRuleTemplateWhenLlmUnavailable() {
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);

        Order o = new Order();
        o.setId(5L);
        o.setStudioId(1L);
        o.setCustomerId(9L);
        o.setShootType("婚纱写真");
        o.setAmount(new BigDecimal("2999"));
        o.setDepositAmount(new BigDecimal("1000"));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));

        Customer c = new Customer();
        c.setId(9L);
        c.setName("王小姐");
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L)).thenReturn(Optional.of(c));

        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM api-key 未配置"));

        CommResponse resp = service.generate(new CommRequest(5L, null, CommScenario.URGE_FINAL), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().contains("王小姐"));
        assertTrue(resp.getText().contains("1999")); // 尾款 = 2999 - 1000
        assertEquals(CommScenario.URGE_FINAL, resp.getScenario());
    }

    @Test
    void generateRepurchaseReusesSameEndpoint() {
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);

        Customer c = new Customer();
        c.setId(7L);
        c.setName("王小姐");
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 7L)).thenReturn(Optional.of(c));

        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(new CommRequest(null, 7L, CommScenario.REPURCHASE), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().contains("一周年"));
    }

    @Test
    void generateRequiresProAndRejectsFreeStudio() {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.PRO_REQUIRED, "该功能为专业版专属"))
                .when(quotaService).requirePro(1L);

        assertThrows(BizException.class,
                () -> service.generate(new CommRequest(5L, null, CommScenario.URGE_FINAL), 1L));
    }

    // ===================================================================
    // 本轮新增：补齐订单/客户定位分支、LLM 成功路径、各场景降级话术
    // ===================================================================

    private Order order(Long id, Long studioId, Long customerId, String shootType) {
        Order o = new Order();
        o.setId(id);
        o.setStudioId(studioId);
        o.setCustomerId(customerId);
        o.setTitle("订单" + id);
        o.setShootType(shootType);
        return o;
    }

    private Customer customer(Long id, String name) {
        Customer c = new Customer();
        c.setId(id);
        c.setName(name);
        return c;
    }

    // ---------- 订单 / 客户定位分支 ----------

    /** orderId 命中但 studioId 不匹配 → filter 为 false → NOT_FOUND("订单不存在")。 */
    @Test
    void generateThrowsNotFoundWhenOrderBelongsToAnotherStudio() {
        doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 999L, 9L, "婚纱")));

        BizException ex = assertThrows(BizException.class,
                () -> service.generate(new CommRequest(5L, null, CommScenario.URGE_FINAL), 1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("订单不存在", ex.getMessage());
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    /** orderId 不存在 → orElseThrow → NOT_FOUND。 */
    @Test
    void generateThrowsNotFoundWhenOrderAbsent() {
        doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> service.generate(new CommRequest(404L, null, CommScenario.URGE_FINAL), 1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    /** 订单存在但客户已被软删 → customer 为 null → 称呼降级为"客户"。 */
    @Test
    void generateUsesDefaultNameWhenCustomerMissing() {
        doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, 9L, "写真")));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.empty());
        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(
                new CommRequest(5L, null, CommScenario.URGE_DEPOSIT), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().startsWith("客户您好"));
        assertTrue(resp.getText().contains("写真"));
    }

    /** 客户存在但 name 为 null → {@code customer.getName() != null} 为 false → 称呼"客户"。 */
    @Test
    void generateUsesDefaultNameWhenCustomerNameNull() {
        doNothing().when(quotaService).requirePro(1L);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 7L))
                .thenReturn(Optional.of(customer(7L, null)));
        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(
                new CommRequest(null, 7L, CommScenario.REPURCHASE), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().startsWith("客户您好"));
    }

    /** 非复购场景且未指定订单/客户 → VALIDATION("请指定订单")。 */
    @Test
    void generateThrowsValidationWhenOrderMissingForNonRepurchase() {
        doNothing().when(quotaService).requirePro(1L);

        BizException ex = assertThrows(BizException.class,
                () -> service.generate(new CommRequest(null, null, CommScenario.FAQ), 1L));
        assertEquals(ErrorCode.VALIDATION.getCode(), ex.getCode());
        assertEquals("请指定订单", ex.getMessage());
    }

    /** 复购场景但客户查不到 → VALIDATION("请指定客户")。 */
    @Test
    void generateThrowsValidationWhenCustomerMissingForRepurchase() {
        doNothing().when(quotaService).requirePro(1L);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 7L))
                .thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> service.generate(new CommRequest(null, 7L, CommScenario.REPURCHASE), 1L));
        assertEquals(ErrorCode.VALIDATION.getCode(), ex.getCode());
        assertEquals("请指定客户", ex.getMessage());
    }

    // ---------- LLM 成功路径 ----------

    /** LLM 可用 → 直接返回模型文本，fallback=false。 */
    @Test
    void generateReturnsLlmTextWhenLlmAvailable() {
        doNothing().when(quotaService).requirePro(1L);
        Order o = order(5L, 1L, 9L, "婚纱写真");
        o.setAmount(new BigDecimal("3000"));
        o.setDepositAmount(new BigDecimal("500"));
        o.setShootDate(LocalDate.of(2025, 6, 1));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));
        Customer c = customer(9L, "王小姐");
        c.setSourceChannel("小红书");
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(c));
        when(llmClient.chat(anyString(), anyString())).thenReturn("亲，尾款请微信转我~");

        CommResponse resp = service.generate(
                new CommRequest(5L, null, CommScenario.URGE_FINAL), 1L);

        assertFalse(resp.isFallback());
        assertEquals("亲，尾款请微信转我~", resp.getText());
        assertEquals(CommScenario.URGE_FINAL, resp.getScenario());
    }

    /** LLM 可用 + 仅客户（复购）→ 成功路径下 order 为 null 的 Prompt 组装分支。 */
    @Test
    void generateRepurchaseReturnsLlmTextWithoutOrder() {
        doNothing().when(quotaService).requirePro(1L);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 7L))
                .thenReturn(Optional.of(customer(7L, "李太太")));
        when(llmClient.chat(anyString(), anyString())).thenReturn("李太太，好久不见，来续拍呀~");

        CommResponse resp = service.generate(
                new CommRequest(null, 7L, CommScenario.REPURCHASE), 1L);

        assertFalse(resp.isFallback());
        assertEquals("李太太，好久不见，来续拍呀~", resp.getText());
        assertEquals(CommScenario.REPURCHASE, resp.getScenario());
    }

    // ---------- 各场景降级话术分支 ----------

    /** URGE_DEPOSIT 降级：话术含客户名与拍摄类型。 */
    @Test
    void generateUrgeDepositFallbackMentionsShootType() {
        doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, 9L, "亲子照")));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer(9L, "李太太")));
        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(
                new CommRequest(5L, null, CommScenario.URGE_DEPOSIT), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().contains("李太太"));
        assertTrue(resp.getText().contains("亲子照"));
        assertTrue(resp.getText().contains("定金"));
        assertEquals(CommScenario.URGE_DEPOSIT, resp.getScenario());
    }

    /** PRE_SHOOT 降级：话术含拍摄日（shootDate 非空分支）。 */
    @Test
    void generatePreShootFallbackMentionsShootDate() {
        doNothing().when(quotaService).requirePro(1L);
        Order o = order(5L, 1L, 9L, "证件照");
        o.setShootDate(LocalDate.of(2025, 6, 1));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer(9L, "赵先生")));
        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(
                new CommRequest(5L, null, CommScenario.PRE_SHOOT), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().contains("拍摄安排在"));
        assertTrue(resp.getText().contains("2025-06-01"));
        assertTrue(resp.getText().contains("赵先生"));
    }

    /** PRE_SHOOT 降级 + shootType 为空 → type 兜底"拍摄"、档期兜底"约定档期"。 */
    @Test
    void generatePreShootFallbackUsesDefaultsWhenOrderFieldsNull() {
        doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, 9L, null)));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer(9L, "钱女士")));
        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(
                new CommRequest(5L, null, CommScenario.PRE_SHOOT), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().contains("约定档期"));
        assertTrue(resp.getText().contains("拍摄"));
    }

    /** DELIVER_REVIEW 降级：话术含"成片已交付"。 */
    @Test
    void generateDeliverReviewFallback() {
        doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, 9L, "写真")));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer(9L, "孙小姐")));
        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(
                new CommRequest(5L, null, CommScenario.DELIVER_REVIEW), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().contains("成片已交付"));
        assertTrue(resp.getText().contains("孙小姐"));
        assertEquals(CommScenario.DELIVER_REVIEW, resp.getScenario());
    }

    /** FAQ 降级：话术含答疑口径。 */
    @Test
    void generateFaqFallback() {
        doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, 9L, "旅拍")));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer(9L, "周先生")));
        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(
                new CommRequest(5L, null, CommScenario.FAQ), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().contains("疑问"));
        assertTrue(resp.getText().contains("旅拍"));
        assertEquals(CommScenario.FAQ, resp.getScenario());
    }

    /** URGE_FINAL 降级 + 金额均为空 → balance/fmt 的 null 兜底分支，尾款显示 ¥0。 */
    @Test
    void generateUrgeFinalFallbackWithNullAmountsShowsZero() {
        doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, 9L, "写真")));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer(9L, "吴小姐")));
        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("LLM 不可用"));

        CommResponse resp = service.generate(
                new CommRequest(5L, null, CommScenario.URGE_FINAL), 1L);

        assertTrue(resp.isFallback());
        assertTrue(resp.getText().contains("¥0"));
        assertTrue(resp.getText().contains("吴小姐"));
    }
}
