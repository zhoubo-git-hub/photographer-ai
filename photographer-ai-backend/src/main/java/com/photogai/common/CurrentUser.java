package com.photogai.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前用户上下文工具类。从 Spring Security 上下文提取 {@link JwtUser}。
 *
 * <p>多租户隔离的关键：所有业务 Service 通过 {@link #getStudioId()} 取得当前 studio，
 * 查询强制按该 studio 过滤，禁止跨租户访问。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 取得完整身份对象，未登录返回 {@code null}。 */
    public static JwtUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUser jwtUser) {
            return jwtUser;
        }
        return null;
    }

    public static Long getUserId() {
        JwtUser jwtUser = get();
        return jwtUser == null ? null : jwtUser.getUserId();
    }

    public static Long getStudioId() {
        JwtUser jwtUser = get();
        return jwtUser == null ? null : jwtUser.getStudioId();
    }

    public static String getUsername() {
        JwtUser jwtUser = get();
        return jwtUser == null ? null : jwtUser.getUsername();
    }

    /** 当前用户角色（OWNER/ADMIN/MEMBER/READONLY），未登录返回 {@code null}。 */
    public static String getRole() {
        JwtUser jwtUser = get();
        return jwtUser == null ? null : jwtUser.getRole();
    }
}
