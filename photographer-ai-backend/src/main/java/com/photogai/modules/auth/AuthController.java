package com.photogai.modules.auth;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.auth.dto.LoginRequest;
import com.photogai.modules.auth.dto.RegisterRequest;
import com.photogai.modules.auth.dto.WechatBindRequest;
import com.photogai.modules.auth.dto.WechatLoginRequest;
import com.photogai.modules.auth.dto.WechatLoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（白名单，无需 JWT；例外：{@code /wechat/bind} 需登录态）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WechatService wechatService;

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    /**
     * 微信登录（三端统一）：小程序 {@code wx.login}、App / Web 开放平台 OAuth 均调此接口。
     *
     * <p>后端解析 openid + unionid；命中同一 unionid 即同一 studio；
     * {@code bindToken} 非空表示绑定到已登录的密码账号，不新建账号。
     */
    @PostMapping("/wechat/login")
    public Result<WechatLoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest req) {
        return Result.ok(wechatService.loginByCode(req));
    }

    /** 已登录用户绑定微信（需 JWT）：写入 {@code user_wechat}，多租户按当前 studio 归属。 */
    @PostMapping("/wechat/bind")
    public Result<AuthResponse> wechatBind(@Valid @RequestBody WechatBindRequest req) {
        return Result.ok(wechatService.bind(CurrentUser.getStudioId(), CurrentUser.getUserId(), req));
    }
}
