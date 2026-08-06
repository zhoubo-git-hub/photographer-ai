package com.photogai.common;

import lombok.Getter;

/**
 * 当前登录用户的轻量身份载体，作为 Spring Security 的 principal 存入上下文。
 *
 * <p>由 {@link com.photogai.config.JwtFilter} 在每次请求时从 JWT 解析并填充。
 */
@Getter
public class JwtUser {

    private final Long userId;
    private final Long studioId;
    private final String username;
    private final String role;

    public JwtUser(Long userId, Long studioId, String username, String role) {
        this.userId = userId;
        this.studioId = studioId;
        this.username = username;
        this.role = role;
    }
}
