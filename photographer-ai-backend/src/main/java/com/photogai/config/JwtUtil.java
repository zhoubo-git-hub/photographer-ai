package com.photogai.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 工具：签发与解析。密钥仅来自配置/环境变量，不落库。
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 Token，载荷含 userId / studioId / role。 */
    public String generateToken(Long userId, Long studioId, String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("sid", studioId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /** 解析 Token，非法/过期抛出异常。 */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
