package com.photogai.modules.ai;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.ai.dto.QuoteRequest;
import com.photogai.modules.ai.dto.QuoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 报价接口。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiQuoteController {

    private final AiQuoteService aiQuoteService;

    @PostMapping("/quote")
    public Result<QuoteResponse> quote(@Valid @RequestBody QuoteRequest req) {
        return Result.ok(aiQuoteService.quote(req, CurrentUser.getStudioId()));
    }
}
