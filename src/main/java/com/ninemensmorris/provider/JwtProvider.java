package com.ninemensmorris.provider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
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

    // JWT 토큰 생성
    public String generateToken(String email, Long expirationPeriod) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationPeriod);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(this.getSigningKey())
                .compact();
    }

    // 액세스 토큰 생성
    public String generateAccessToken(String email) {
        return generateToken(email, accessTokenExpirationPeriod);
    }

    // 리프레시 토큰 생성
    public String generateRefreshToken(String email) {
        return generateToken(email, refreshTokenExpirationPeriod);
    }

    // JWT 토큰 검증
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

    // JWT 토큰에서 subject(사용자 식별자) 추출
    public String extractSubject(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(this.getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public void setAccessTokenHeader(HttpServletResponse response, String accessToken) {
        response.setHeader(accessHeader, accessToken);
    }

    public void setRefreshTokenHeader(HttpServletResponse response, String refreshToken) {
        response.setHeader(refreshHeader, refreshToken);
    }

    public void sendAccessAndRefreshToken(HttpServletResponse response, String accessToken, String refreshToken) {
        response.setStatus(HttpServletResponse.SC_OK);

        setAccessTokenHeader(response, accessToken);
        setRefreshTokenHeader(response, refreshToken);
        log.info("Access Token and Refresh Token headers are set successfully");
    }
}