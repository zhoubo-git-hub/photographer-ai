package com.photogai.config;

import com.photogai.modules.auth.enums.WechatAppType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 微信登录装配：按终端（MP / APP / WEB）持有 appid / secret，并提供微信接口地址。
 *
 * <p>凭据全部走 {@code application.yml} + 环境变量（{@code WECHAT_MP_APPID} 等），
 * <b>缺省为空字符串</b>，本地/沙箱不配也能正常启动；仅在真正调用微信接口时由
 * {@code WechatService} 校验并给出"未配置"提示，<b>优雅降级不崩</b>。
 *
 * <p>零新增 Maven 依赖：HTTP 调用复用 Spring 内置 {@link RestClient}（同 {@code LlmClient} /
 * {@code WechatPaymentGateway}）。
 */
@Configuration
public class WechatConfig {

    // ===== 小程序（wx.login → code2Session） =====
    @Value("${wechat.mp.appid:}")
    private String mpAppid = "";

    @Value("${wechat.mp.secret:}")
    private String mpSecret = "";

    // ===== 开放平台·移动应用（App OAuth） =====
    @Value("${wechat.app.appid:}")
    private String appAppid = "";

    @Value("${wechat.app.secret:}")
    private String appSecret = "";

    // ===== 开放平台·网站应用（Web 扫码 OAuth） =====
    @Value("${wechat.web.appid:}")
    private String webAppid = "";

    @Value("${wechat.web.secret:}")
    private String webSecret = "";

    // ===== 微信接口地址（可整体切到代理/沙箱域名，不改代码） =====
    /** 小程序登录凭证校验：code → openid / session_key / unionid。 */
    @Value("${wechat.api.code2session:https://api.weixin.qq.com/sns/jscode2session}")
    private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

    /** 开放平台：code → access_token / openid / unionid。 */
    @Value("${wechat.api.oauth2-access-token:https://api.weixin.qq.com/sns/oauth2/access_token}")
    private String oauth2AccessTokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token";

    /** 开放平台：access_token + openid → nickname / headimgurl / unionid。 */
    @Value("${wechat.api.userinfo:https://api.weixin.qq.com/sns/userinfo}")
    private String userinfoUrl = "https://api.weixin.qq.com/sns/userinfo";

    /** 首次微信登录是否自动建 Studio + User（关闭后未绑定的微信登录直接报错，用于强制先注册）。 */
    @Value("${wechat.auto-register:true}")
    private boolean autoRegister = true;

    /** 微信接口专用 RestClient（与 llmRestClient / paymentRestClient 并列，按名注入）。 */
    @Bean
    public RestClient wechatRestClient() {
        return RestClient.builder().build();
    }

    /**
     * 取指定终端的应用凭据。
     *
     * @param appType 终端类型，非空
     * @return 该终端的 appid / secret（可能未配置，调用方用 {@link WechatApp#configured()} 判断）
     */
    public WechatApp appOf(WechatAppType appType) {
        return switch (appType) {
            case MP -> new WechatApp(nullToEmpty(mpAppid), nullToEmpty(mpSecret));
            case APP -> new WechatApp(nullToEmpty(appAppid), nullToEmpty(appSecret));
            case WEB -> new WechatApp(nullToEmpty(webAppid), nullToEmpty(webSecret));
        };
    }

    public String getCode2SessionUrl() {
        return code2SessionUrl;
    }

    public String getOauth2AccessTokenUrl() {
        return oauth2AccessTokenUrl;
    }

    public String getUserinfoUrl() {
        return userinfoUrl;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 单个终端的微信应用凭据。
     *
     * @param appid  应用 ID
     * @param secret 应用密钥（仅后端持有，不下发前端）
     */
    public record WechatApp(String appid, String secret) {

        /** appid 与 secret 均非空才算配置完成。 */
        public boolean configured() {
            return appid != null && !appid.isBlank() && secret != null && !secret.isBlank();
        }
    }
}
