package com.ninemensmorris.security.provider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Getter
@Slf4j
public class JwtProvider {

    @Value("${JWT_SECRET_KEY}")
    private String secretKey;

    @Value("${ACCESS_TOKEN_EXPIRATION}")
    private Long accessTokenExpirationPeriod;

    @Value("${REFRESH_TOKEN_EXPIRATION}")
    private Long refreshTokenExpirationPeriod;

    @Value("${ACCESS_TOKEN_HEADER}")
    private String accessHeader;

    @Value("${REFRESH_TOKEN_HEADER}")
    private String refreshHeader;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(this.secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, Long expirationPeriod) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationPeriod);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(this.getSigningKey())
                .compact();
    }

    public String generateAccessToken(Long userId) {
        return generateToken(userId, accessTokenExpirationPeriod);
    }

    public String generateRefreshToken(Long userId) {
        return generateToken(userId, refreshTokenExpirationPeriod);
    }

    public boolean validateToken(String token) {

        try {
            Jwts.parserBuilder()
                    .setSigningKey(this.getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return true;
        } catch (Exception exception) {
            log.error("Failed to validate JWT token: {}", exception.getMessage());
            return false;
        }
    }

    public Long extractSubject(String token) {
        String userIdString = Jwts.parserBuilder()
                .setSigningKey(this.getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();

        return Long.parseLong(userIdString); // 추출한 식별자 아이디를 Long 타입으로 변환하여 반환
    }
}
