package com.photogai.modules.auth.dto;

import com.photogai.modules.studio.dto.StudioDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信登录响应：结构与 {@link AuthResponse} 对齐（token + user + studio），
 * 额外给三端一点引导信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatLoginResponse {

    /** JWT，写法与账号密码登录完全一致。 */
    private String token;

    /** 当前用户。 */
    private UserDTO user;

    /** 所属工作室（多租户根）。 */
    private StudioDTO studio;

    /** 是否本次微信首登自动建号（前端可引导补全工作室名/头像）。 */
    private Boolean isNewUser;

    /** 是否建议引导"绑定已有密码账号"（自动建号时为 true）。 */
    private Boolean needBind;

    /** 由既有 {@link AuthResponse} 结构平移，避免两处登录返回体漂移。 */
    public static WechatLoginResponse from(AuthResponse auth, boolean isNewUser, boolean needBind) {
        if (auth == null) {
            return null;
        }
        return WechatLoginResponse.builder()
                .token(auth.getToken())
                .user(auth.getUser())
                .studio(auth.getStudio())
                .isNewUser(isNewUser)
                .needBind(needBind)
                .build();
    }
}
