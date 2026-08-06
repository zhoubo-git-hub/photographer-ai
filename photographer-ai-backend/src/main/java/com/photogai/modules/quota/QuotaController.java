package com.photogai.modules.quota;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.quota.dto.QuotaDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 额度查询接口。
 */
@RestController
@RequestMapping("/api/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    @GetMapping
    public Result<QuotaDTO> get() {
        return Result.ok(quotaService.getQuota(CurrentUser.getStudioId()));
    }
}
