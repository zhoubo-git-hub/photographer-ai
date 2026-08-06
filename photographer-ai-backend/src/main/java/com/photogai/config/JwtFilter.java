package com.photogai.config;

import com.photogai.common.JwtUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.Claims;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器：从 {@code Authorization: Bearer <jwt>} 解析身份并写入 Security 上下文。
 *
 * <p>解析失败仅留空上下文，由 {@link SecurityConfig} 的认证入口统一返回 401。
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parse(token);
                Long userId = claims.get("uid", Long.class);
                Long studioId = claims.get("sid", Long.class);
                String username = claims.getSubject();
                String role = claims.get("role", String.class);
                JwtUser jwtUser = new JwtUser(userId, studioId, username, role);
                List<SimpleGrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "OWNER" : role)));
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(jwtUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // 无效 token：保持未认证，后续由 Security 拦截
            }
        }
        chain.doFilter(request, response);
    }
}
