package com.photogai.modules.order;

import com.photogai.common.CurrentUser;
import com.photogai.common.PageData;
import com.photogai.common.Result;
import com.photogai.modules.order.dto.ConflictDTO;
import com.photogai.modules.order.dto.OrderCreateRequest;
import com.photogai.modules.order.dto.OrderDTO;
import com.photogai.modules.order.dto.OrderUpdateRequest;
import com.photogai.modules.order.dto.StatusChangeRequest;
import com.photogai.modules.order.enums.OrderStatus;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口：CRUD、状态流、档期冲突查询。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ScheduleConflictService scheduleConflictService;

    @GetMapping
    public Result<PageData<OrderDTO>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(orderService.list(CurrentUser.getStudioId(), status, page, size));
    }

    @PostMapping
    public Result<OrderDTO> create(@Valid @RequestBody OrderCreateRequest req) {
        return Result.ok(orderService.create(
                CurrentUser.getStudioId(), CurrentUser.getUserId(), req));
    }

    @GetMapping("/conflict")
    public Result<List<ConflictDTO>> conflict(
            @RequestParam(required = false) LocalDate shootDate,
            @RequestParam(required = false) LocalDate shootEndDate,
            @RequestParam(required = false) Long excludeOrderId) {
        return Result.ok(scheduleConflictService.checkConflict(
                CurrentUser.getStudioId(), shootDate, shootEndDate, excludeOrderId));
    }

    @GetMapping("/{id}")
    public Result<OrderDTO> detail(@PathVariable Long id) {
        return Result.ok(orderService.get(CurrentUser.getStudioId(), id));
    }

    @PutMapping("/{id}")
    public Result<OrderDTO> update(@PathVariable Long id,
                                   @Valid @RequestBody OrderUpdateRequest req) {
        return Result.ok(orderService.update(CurrentUser.getStudioId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orderService.delete(CurrentUser.getStudioId(), id);
        return Result.ok();
    }

    /** B6 分配订单给团队成员（团队版；memberId 传 null 表示回退未分配）。 */
    @PostMapping("/{id}/assign")
    public Result<OrderDTO> assign(@PathVariable Long id,
                                   @RequestBody java.util.Map<String, Long> body) {
        Long memberId = body == null ? null : body.get("memberId");
        return Result.ok(orderService.assign(
                CurrentUser.getStudioId(), id, memberId,
                CurrentUser.getUserId(), CurrentUser.getRole()));
    }

    @PostMapping("/{id}/status")
    public Result<OrderDTO> changeStatus(@PathVariable Long id,
                                         @Valid @RequestBody StatusChangeRequest req) {
        Long operator = Optional.ofNullable(req.getOperatorId())
                .orElse(CurrentUser.getUserId());
        return Result.ok(orderService.changeStatus(
                CurrentUser.getStudioId(), id, req, operator));
    }
}
