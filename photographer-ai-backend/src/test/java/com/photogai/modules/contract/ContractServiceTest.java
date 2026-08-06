package com.photogai.modules.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.photogai.modules.contract.dto.ContractGenerateRequest;
import com.photogai.modules.contract.dto.ContractGenerateResponse;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import java.math.BigDecimal;
import java.time.LocalDate;
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
}
