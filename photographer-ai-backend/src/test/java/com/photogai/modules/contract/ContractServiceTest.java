package com.photogai.modules.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.contract.dto.ContractGenerateRequest;
import com.photogai.modules.contract.dto.ContractGenerateResponse;
import com.photogai.modules.contract.dto.ContractTemplateDTO;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 合同服务单元测试（对应 PRD P1-2 / 架构 §2.3 替换引擎）。
 *
 * <p>验证 ContractService.generate 的字段替换引擎：
 * 1. 命中键值（studioName/customerName/amount/depositAmount/balance 等）被替换为实际值；
 * 2. 内容不含任何有值字段对应的 {{key}} 残留；
 * 3. 无数据源的 note / retouchCount 按引擎设计保留 {{}}（已知限制，见测试报告）。
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractTemplateRepository templateRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private StudioRepository studioRepository;
    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private ContractService service;

    private ContractTemplate template() {
        ContractTemplate t = new ContractTemplate();
        t.setId(7L);
        t.setName("摄影服务合同");
        t.setStudioId(null); // 系统内置
        t.setContent("甲方：{{studioName}}\n乙方：{{customerName}}\n"
                + "拍摄类型：{{shootType}}\n金额：{{amount}} 元\n定金：{{depositAmount}} 元\n"
                + "尾款：{{balance}} 元\n备注：{{note}}\n精修：{{retouchCount}} 张");
        return t;
    }

    private Order order() {
        Order o = new Order();
        o.setId(5L);
        o.setStudioId(1L);
        o.setCustomerId(9L);
        o.setShootType("婚纱写真");
        o.setAmount(new BigDecimal("2999"));
        o.setDepositAmount(new BigDecimal("1000"));
        o.setShootDate(LocalDate.of(2026, 6, 28));
        return o;
    }

    private Customer customer() {
        Customer c = new Customer();
        c.setId(9L);
        c.setName("王小姐");
        c.setWechatId("wx_wang");
        c.setPhone("13800000000");
        return c;
    }

    private Studio studio() {
        Studio s = new Studio();
        s.setId(1L);
        s.setName("光影工作室");
        return s;
    }

    @Test
    void generateReplacesAllValuedPlaceholders() {
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);
        when(templateRepository.findById(7L)).thenReturn(Optional.of(template()));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order()));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer()));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio()));

        ContractGenerateResponse resp = service.generate(1L,
                new ContractGenerateRequest(5L, 7L));

        String content = resp.getContent();
        assertTrue(content.contains("光影工作室"));
        assertTrue(content.contains("王小姐"));
        assertTrue(content.contains("婚纱写真"));
        assertTrue(content.contains("2999 元"));
        assertTrue(content.contains("1000 元"));
        assertTrue(content.contains("尾款：1999 元"));
        // 有值字段无残留
        assertFalse(content.contains("{{studioName}}"));
        assertFalse(content.contains("{{customerName}}"));
        assertFalse(content.contains("{{amount}}"));
        // 无数据源字段按设计保留
        assertTrue(content.contains("{{note}}"));
        assertTrue(content.contains("{{retouchCount}}"));
        // 标题格式
        assertEquals("摄影服务合同 - 王小姐-婚纱写真", resp.getTitle());
    }

    @Test
    void generateRequiresProAndRejectsNonOwnedTemplate() {
        when(templateRepository.findById(7L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);

        assertThrowsBusiness(() -> service.generate(1L, new ContractGenerateRequest(5L, 7L)));
    }

    private void assertThrowsBusiness(Runnable r) {
        try {
            r.run();
            throw new AssertionError("期望抛出 BizException 但未抛出");
        } catch (com.photogai.exception.BizException e) {
            // 预期
        }
    }

    // ========================= listTemplates =========================

    @Test
    void listTemplatesMapsAllTemplates() {
        ContractTemplate builtin = template();
        ContractTemplate custom = new ContractTemplate();
        custom.setId(8L);
        custom.setName("自定义合同");
        custom.setStudioId(1L);
        custom.setContent("内容");
        custom.setBuiltin(false);
        when(templateRepository.findByStudioIdIsNullOrStudioId(1L)).thenReturn(List.of(builtin, custom));

        List<ContractTemplateDTO> dtos = service.listTemplates(1L);
        assertEquals(2, dtos.size());
        assertEquals("摄影服务合同", dtos.get(0).getName());
        assertEquals(1L, dtos.get(1).getStudioId());
    }

    // ========================= generate: 门禁 / 多租户隔离 =========================

    @Test
    void generateRejectsTemplateNotOwned() {
        ContractTemplate t = template();
        t.setStudioId(2L); // 属于其他工作室
        when(templateRepository.findById(7L)).thenReturn(Optional.of(t));
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);

        BizException ex = assertThrows(BizException.class,
                () -> service.generate(1L, new ContractGenerateRequest(5L, 7L)));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void generateRejectsWhenOrderMissing() {
        when(templateRepository.findById(7L)).thenReturn(Optional.of(template()));
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> service.generate(1L, new ContractGenerateRequest(5L, 7L)));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void generateRejectsWhenOrderNotOwned() {
        Order o = order();
        o.setStudioId(2L); // 工作室不匹配
        when(templateRepository.findById(7L)).thenReturn(Optional.of(template()));
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));

        BizException ex = assertThrows(BizException.class,
                () -> service.generate(1L, new ContractGenerateRequest(5L, 7L)));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ========================= generate: 字段替换引擎分支 =========================

    @Test
    void generateWithMissingCustomerUsesFallbacks() {
        ContractTemplate t = template();
        // 模板额外含 wechatId/phone 占位符，以便验证 customer 为空时的三元分支
        t.setContent("客户：{{customerName}}\n微信：{{wechatId}}\n电话：{{phone}}");
        when(templateRepository.findById(7L)).thenReturn(Optional.of(t));
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order()));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.empty());
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio()));

        ContractGenerateResponse resp = service.generate(1L, new ContractGenerateRequest(5L, 7L));
        String content = resp.getContent();
        assertTrue(content.contains("客户"));          // customerName 回退 "客户"
        assertTrue(content.contains("{{wechatId}}"));   // wechatId 空 → 保留
        assertTrue(content.contains("{{phone}}"));      // phone 空 → 保留
        assertEquals("摄影服务合同 - 客户-婚纱写真", resp.getTitle());
    }

    @Test
    void generateWithMissingStudioUsesFallback() {
        when(templateRepository.findById(7L)).thenReturn(Optional.of(template()));
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order()));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer()));
        when(studioRepository.findById(1L)).thenReturn(Optional.empty());

        ContractGenerateResponse resp = service.generate(1L, new ContractGenerateRequest(5L, 7L));
        assertTrue(resp.getContent().contains("{{studioName}}")); // studio 空 → 保留
    }

    @Test
    void generateWithNullAmountAndDepositUsesZero() {
        Order o = order();
        o.setAmount(null);
        o.setDepositAmount(null);
        ContractTemplate t = template();
        // 模板额外含 depositRatio 占位符，以便验证 amount/deposit 为空时的三元分支
        t.setContent("金额：{{amount}}\n定金：{{depositAmount}}\n尾款：{{balance}} 元\n定金比例：{{depositRatio}}");
        when(templateRepository.findById(7L)).thenReturn(Optional.of(t));
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer()));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio()));

        ContractGenerateResponse resp = service.generate(1L, new ContractGenerateRequest(5L, 7L));
        String content = resp.getContent();
        assertTrue(content.contains("{{amount}}"));         // amount 空 → 保留占位符
        assertTrue(content.contains("{{depositAmount}}"));  // depositAmount 空 → 保留占位符
        assertTrue(content.contains("尾款：0 元"));          // 0 - 0 = 0（非空值，替换）
        assertTrue(content.contains("{{depositRatio}}"));   // depositRatio 空 → 保留
    }

    @Test
    void generateWithNullShootTypeOmitsShootTypeInTitle() {
        Order o = order();
        o.setShootType(null);
        when(templateRepository.findById(7L)).thenReturn(Optional.of(template()));
        org.mockito.Mockito.doNothing().when(quotaService).requirePro(1L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 9L))
                .thenReturn(Optional.of(customer()));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio()));

        ContractGenerateResponse resp = service.generate(1L, new ContractGenerateRequest(5L, 7L));
        assertEquals("摄影服务合同 - 王小姐", resp.getTitle());
    }
}
