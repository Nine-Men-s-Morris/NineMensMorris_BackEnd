package com.ninemensmorris.security;

import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        authenticate(request);
        filterChain.doFilter(request, response);
    }

    /** 인증에 실패해도 체인은 계속 진행. 접근 제어는 SecurityConfig 의 인가 규칙이 담당 */
    private void authenticate(HttpServletRequest request) {
        String token = parseTokenFromCookie(request);
        // 검증 실패 사유는 JwtProvider 가 debug 로 남김. 여기서 또 남기면 요청당 2줄
        if (token == null || !jwtProvider.validateToken(token)) {
            return;
        }

        try {
            Long userId = jwtProvider.extractSubject(token);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                // 서명은 유효한데 사용자가 없음. 탈퇴했거나 DB 가 초기화된 경우
                log.warn("유효한 토큰이지만 사용자를 찾을 수 없음 userId={}", userId);
                return;
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority(user.getRole()));

            AbstractAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authenticationToken);
            SecurityContextHolder.setContext(securityContext);
        } catch (Exception exception) {
            log.warn("JWT 인증 처리 실패: {}", exception.toString());
        }
    }

    private String parseTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
