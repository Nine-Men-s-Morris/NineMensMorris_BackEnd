package com.ninemensmorris.security.provider;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtProvider {

    @Value("${JWT_SECRET_KEY}")
    private String secretKey;

    @Value("${ACCESS_TOKEN_EXPIRATION}")
    private Long accessTokenExpirationPeriod;

    private SecretKey signingKey;
    private JwtParser parser;

    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(this.secretKey));
        this.parser = Jwts.parser().verifyWith(this.signingKey).build();
    }

    public String generateToken(Long userId, Long expirationPeriod) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationPeriod);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(this.signingKey)
                .compact();
    }

    public String generateAccessToken(Long userId) {
        return generateToken(userId, accessTokenExpirationPeriod);
    }

    public boolean validateToken(String token) {
        try {
            this.parser.parseSignedClaims(token);
            return true;
        } catch (Exception exception) {
            // 만료된 토큰으로 접속하는 것은 정상 흐름이다. ERROR 로 남기면 로그가 도배된다.
            log.debug("JWT 검증 실패: {}", exception.getMessage());
            return false;
        }
    }

    public Long extractSubject(String token) {
        String userIdString = this.parser.parseSignedClaims(token).getPayload().getSubject();
        return Long.parseLong(userIdString);
    }
}
