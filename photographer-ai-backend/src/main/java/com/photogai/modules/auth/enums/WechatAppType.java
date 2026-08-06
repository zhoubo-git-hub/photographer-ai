package com.photogai.modules.auth.enums;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import java.util.Locale;

/**
 * 微信终端类型：三端打通的入口区分。
 *
 * <ul>
 *   <li>{@link #MP} 微信小程序（{@code wx.login} → code，走 {@code auth.code2Session}）</li>
 *   <li>{@link #APP} 移动 App（微信开放平台移动应用 OAuth）</li>
 *   <li>{@link #WEB} 网站应用（微信开放平台扫码 OAuth）</li>
 * </ul>
 *
 * <p>三端只要绑定在同一微信开放平台账号下，解析出的 {@code union_id} 一致，即归属同一 studio。
 */
public enum WechatAppType {

    /** 微信小程序。 */
    MP,

    /** 移动 App（安卓 / iOS）。 */
    APP,

    /** 网站应用（扫码登录）。 */
    WEB;

    /** 小程序走 {@code code2Session}，App / Web 走开放平台 {@code sns.oauth2}。 */
    public boolean isMiniProgram() {
        return this == MP;
    }

    /**
     * 宽松解析（忽略大小写与首尾空白）。
     *
     * @param raw 前端传入的 appType 字符串
     * @throws BizException 当为空或不在枚举范围内
     */
    public static WechatAppType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION, "appType 不能为空（可选 MP/APP/WEB）");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.VALIDATION, "不支持的 appType：" + raw + "（可选 MP/APP/WEB）");
        }
    }
}
