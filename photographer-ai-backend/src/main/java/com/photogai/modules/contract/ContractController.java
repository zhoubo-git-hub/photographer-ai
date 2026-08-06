package com.photogai.modules.contract;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.contract.dto.ContractGenerateRequest;
import com.photogai.modules.contract.dto.ContractGenerateResponse;
import com.photogai.modules.contract.dto.ContractTemplateDTO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合同接口：模板列表（全员可见）+ 生成（PRO）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    /** 内置 + 本工作室模板列表（E6，全员可见）。 */
    @GetMapping("/contract-templates")
    public Result<List<ContractTemplateDTO>> templates() {
        return Result.ok(contractService.listTemplates(CurrentUser.getStudioId()));
    }

    /** 套模板生成合同（E7，PRO 门禁）。 */
    @PostMapping("/contracts/generate")
    public Result<ContractGenerateResponse> generate(@Valid @RequestBody ContractGenerateRequest req) {
        return Result.ok(contractService.generate(CurrentUser.getStudioId(), req));
    }
}
