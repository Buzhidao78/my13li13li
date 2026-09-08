package com.example.practice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：负责生成 token、解析 token
 * token 里保存了：用户 id（subject）+ 用户名（claim），并用密钥签名防篡改
 */
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    /** token 有效期（毫秒） */
    @Value("${jwt.expire}")
    private Long expire;

    /** 根据配置的 secret 生成签名密钥 */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token
     *
     * @param userId   用户 id（放进 subject，后续按它查用户）
     * @param username 用户名（可选，仅作为附加信息；手机号注册的用户没有用户名时可为 null）
     * @return JWT 字符串
     */
    public String createToken(Long userId, String username) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                // subject 存用户 id，方便后续取出
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expire));
        // 用户名可能为 null（纯手机号注册的用户），有值才写入
        if (username != null) {
            builder.claim("username", username);
        }
        return builder.signWith(key()).compact();
    }

    /**
     * 解析 token，返回载荷（Claims）
     * token 无效或过期时会抛出异常，由调用方处理
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
