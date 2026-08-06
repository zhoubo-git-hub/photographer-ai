package com.photogai.modules.ai;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.ai.dto.CommRequest;
import com.photogai.modules.ai.dto.CommResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 沟通助手接口（PRO + LLM，复购话术复用此端点传 scenario=REPURCHASE）。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiCommController {

    private final AiCommService aiCommService;

    @PostMapping("/comm")
    public Result<CommResponse> comm(@Valid @RequestBody CommRequest req) {
        return Result.ok(aiCommService.generate(req, CurrentUser.getStudioId()));
    }
}
