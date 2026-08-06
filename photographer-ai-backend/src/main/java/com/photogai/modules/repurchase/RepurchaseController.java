package com.photogai.modules.repurchase;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.repurchase.dto.RepurchaseTaskDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 复购引擎接口（PRO 专属）。
 *
 * <p>E8 查询当前工作室复购任务；PRO 门禁在 {@link RepurchaseService#listTasks} 内，
 * 免费版访问将抛 {@code PRO_REQUIRED}(403)，由前端统一弹升级引导。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RepurchaseController {

    private final RepurchaseService repurchaseService;

    @GetMapping("/repurchases")
    public Result<List<RepurchaseTaskDTO>> list() {
        return Result.ok(repurchaseService.listTasks(CurrentUser.getStudioId()));
    }
}
