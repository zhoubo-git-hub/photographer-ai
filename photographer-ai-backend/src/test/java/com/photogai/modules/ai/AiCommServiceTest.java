package com.photogai.modules.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}
