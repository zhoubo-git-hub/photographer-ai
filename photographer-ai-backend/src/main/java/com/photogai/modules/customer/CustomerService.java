package com.photogai.modules.customer;

import com.photogai.common.PageData;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.customer.dto.CustomerCreateRequest;
import com.photogai.modules.customer.dto.CustomerDTO;
import com.photogai.modules.customer.dto.CustomerUpdateRequest;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.dto.OrderDTO;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户库服务：增删改查、软删除（删除前校验无进行中订单）、详情聚合历史订单。
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    /** 视为"进行中"的状态（删除客户时不允许存在）。 */
    private static final List<OrderStatus> IN_PROGRESS = List.of(
            OrderStatus.CONSULT, OrderStatus.DEPOSIT, OrderStatus.SHOOT, OrderStatus.EDIT);

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PageData<CustomerDTO> list(Long studioId, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Customer> result = customerRepository.search(studioId,
                keyword == null ? "" : keyword, pageable);
        List<CustomerDTO> content = result.getContent().stream()
                .map(c -> withStats(c, studioId))
                .collect(Collectors.toList());
        return PageData.<CustomerDTO>builder()
                .content(content)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .number(result.getNumber())
                .size(result.getSize())
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerDTO detail(Long studioId, Long id) {
        Customer customer = customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(studioId, id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "客户不存在"));
        CustomerDTO dto = withStats(customer, studioId);
        List<OrderDTO> orders = orderRepository
                .findByStudioIdAndCustomerIdAndDeletedAtIsNull(studioId, id).stream()
                .sorted(Comparator.comparing(
                        (Order o) -> o.getShootDate() == null
                                ? LocalDateTime.MAX : o.getShootDate().atStartOfDay()))
                .map(o -> OrderDTO.from(o, customer.getName(), null))
                .collect(Collectors.toList());
        dto.setOrders(orders);
        return dto;
    }

    @Transactional
    public CustomerDTO create(Long studioId, CustomerCreateRequest req) {
        Customer customer = new Customer();
        customer.setStudioId(studioId);
        customer.setName(req.getName());
        customer.setWechatId(req.getWechatId());
        customer.setPhone(req.getPhone());
        customer.setTags(req.getTags());
        customer.setNote(req.getNote());
        customer.setLastShootDate(req.getLastShootDate());
        customer.setRepurchaseCycleDays(req.getRepurchaseCycleDays());
        customer.setBirthday(req.getBirthday());
        customer.setAnniversary(req.getAnniversary());
        customer.setRepurchaseEnabled(
                req.getRepurchaseEnabled() == null ? true : req.getRepurchaseEnabled());
        customer.setSourceChannel(req.getSourceChannel());
        Customer saved = customerRepository.save(customer);
        return CustomerDTO.from(saved);
    }

    @Transactional
    public CustomerDTO update(Long studioId, Long id, CustomerUpdateRequest req) {
        Customer customer = customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(studioId, id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "客户不存在"));
        if (req.getName() != null) {
            customer.setName(req.getName());
        }
        if (req.getWechatId() != null) {
            customer.setWechatId(req.getWechatId());
        }
        if (req.getPhone() != null) {
            customer.setPhone(req.getPhone());
        }
        if (req.getTags() != null) {
            customer.setTags(req.getTags());
        }
        if (req.getNote() != null) {
            customer.setNote(req.getNote());
        }
        if (req.getLastShootDate() != null) {
            customer.setLastShootDate(req.getLastShootDate());
        }
        if (req.getRepurchaseCycleDays() != null) {
            customer.setRepurchaseCycleDays(req.getRepurchaseCycleDays());
        }
        if (req.getBirthday() != null) {
            customer.setBirthday(req.getBirthday());
        }
        if (req.getAnniversary() != null) {
            customer.setAnniversary(req.getAnniversary());
        }
        if (req.getRepurchaseEnabled() != null) {
            customer.setRepurchaseEnabled(req.getRepurchaseEnabled());
        }
        if (req.getSourceChannel() != null) {
            customer.setSourceChannel(req.getSourceChannel());
        }
        return CustomerDTO.from(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long studioId, Long id) {
        Customer customer = customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(studioId, id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "客户不存在"));
        boolean hasInProgress = orderRepository
                .findByStudioIdAndCustomerIdAndDeletedAtIsNull(studioId, id).stream()
                .anyMatch(o -> IN_PROGRESS.contains(o.getStatus()));
        if (hasInProgress) {
            throw new BizException(ErrorCode.FORBIDDEN, "该客户存在进行中订单，无法删除");
        }
        customer.setDeletedAt(LocalDateTime.now());
        customerRepository.save(customer);
    }

    /** 填充统计字段：历史订单数、最近拍摄日、历史总额。 */
    private CustomerDTO withStats(Customer customer, Long studioId) {
        CustomerDTO dto = CustomerDTO.from(customer);
        List<Order> orders = orderRepository
                .findByStudioIdAndCustomerIdAndDeletedAtIsNull(studioId, customer.getId());
        dto.setOrderCount(orders.size());
        LocalDateTime last = orders.stream()
                .filter(o -> o.getShootDate() != null)
                .map(o -> o.getShootDate().atStartOfDay())
                .max(LocalDateTime::compareTo)
                .orElse(null);
        dto.setLastOrderAt(last);
        BigDecimal total = orders.stream()
                .map(o -> o.getAmount() == null ? BigDecimal.ZERO : o.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setLastAmount(total);
        return dto;
    }
}
