package com.photogai.modules.customer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.customer.dto.CustomerCreateRequest;
import com.photogai.modules.customer.dto.CustomerDTO;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
