package com.photogai.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信登录请求（三端共用）。
 *
 * <p>小程序：{@code wx.login()} 拿 code，{@code appType=MP}；
 * App / Web：微信开放平台 OAuth 回调拿 code，{@code appType=APP|WEB}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatLoginRequest {

    /** 终端类型：MP | APP | WEB。 */
    @NotBlank(message = "appType 不能为空")
    @Pattern(regexp = "MP|APP|WEB", message = "appType 仅支持 MP/APP/WEB")
    private String appType;

    /** 微信登录临时票据（5 分钟有效、仅可用一次）。 */
    @NotBlank(message = "微信 code 不能为空")
    @Size(max = 200, message = "微信 code 长度非法")
    private String code;

    /** 选填·小程序：加密的用户信息（后续解密昵称/手机号用，本期仅透传保留）。 */
    private String encryptedData;

    /** 选填·小程序：加密算法初始向量，与 {@link #encryptedData} 成对出现。 */
    private String iv;

    /**
     * 选填：已登录账号的 JWT。非空表示"把该微信绑定到这个已有账号"，
     * <b>不会</b>新建 Studio / User。
     */
    private String bindToken;
}
