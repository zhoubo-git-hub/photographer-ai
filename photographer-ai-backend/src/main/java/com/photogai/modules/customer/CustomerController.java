package com.photogai.modules.customer;

import com.photogai.common.CurrentUser;
import com.photogai.common.PageData;
import com.photogai.common.Result;
import com.photogai.modules.customer.dto.CustomerCreateRequest;
import com.photogai.modules.customer.dto.CustomerDTO;
import com.photogai.modules.customer.dto.CustomerUpdateRequest;
import jakarta.validation.Valid;
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
 * 客户库接口。
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Result<PageData<CustomerDTO>> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(customerService.list(
                CurrentUser.getStudioId(), keyword, page, size));
    }

    @PostMapping
    public Result<CustomerDTO> create(@Valid @RequestBody CustomerCreateRequest req) {
        return Result.ok(customerService.create(CurrentUser.getStudioId(), req));
    }

    @GetMapping("/{id}")
    public Result<CustomerDTO> detail(@PathVariable Long id) {
        return Result.ok(customerService.detail(CurrentUser.getStudioId(), id));
    }

    @PutMapping("/{id}")
    public Result<CustomerDTO> update(@PathVariable Long id,
                                      @Valid @RequestBody CustomerUpdateRequest req) {
        return Result.ok(customerService.update(CurrentUser.getStudioId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(CurrentUser.getStudioId(), id);
        return Result.ok();
    }
}
