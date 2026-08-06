package com.photogai.modules.ai;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.ai.dto.QuoteCalibrationApplyRequest;
import com.photogai.modules.ai.dto.QuoteCalibrationDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 自学习报价校准接口（受限版）：建议列表 + 人工采纳写回。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class QuoteCalibrationController {

    private final QuoteCalibrationService calibrationService;

    /** D1 校准建议列表（首次触发懒扫描）。 */
    @GetMapping("/quote-calibration")
    public Result<List<QuoteCalibrationDTO>> list() {
        return Result.ok(calibrationService.list(CurrentUser.getStudioId()));
    }

    /** D2 采纳某条建议，写回系数（安全边界外拒绝）。 */
    @PostMapping("/quote-calibration/apply")
    public Result<QuoteCalibrationDTO> apply(@RequestBody QuoteCalibrationApplyRequest req) {
        return Result.ok(calibrationService.apply(CurrentUser.getStudioId(), req.getId()));
    }
}
