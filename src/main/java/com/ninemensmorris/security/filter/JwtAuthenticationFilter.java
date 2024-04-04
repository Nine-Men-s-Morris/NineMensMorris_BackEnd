package com.ninemensmorris.security.filter;

import com.ninemensmorris.security.provider.JwtProvider;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal (HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {

            String token = parseBearerToken(request);
            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }

            boolean validateToken = jwtProvider.validateToken(token);
            if (!validateToken) {
                log.error("JWT token validation failed");
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = jwtProvider.extractSubject(token);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.error("User not found for user id: {}", userId);
                return;
            }

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();

            // 사용자의 이메일을 사용하여 인증 토큰 생성
            AbstractAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(userId, null);
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 생성된 인증 토큰을 보안 컨텍스트에 설정
            securityContext.setAuthentication(authenticationToken);

            // 설정된 보안 컨텍스트를 SecurityContextHolder에 설정
            SecurityContextHolder.setContext(securityContext);

        } catch (Exception exception) {
            log.error("Failed to process JWT token", exception);
        }

        filterChain.doFilter(request, response);
    }

    private String parseBearerToken(HttpServletRequest request) {

        String authorization = request.getHeader("Authorization");
        boolean hasAuthorization = StringUtils.hasText(authorization);
        if (!hasAuthorization) return null;

        boolean isBearer = authorization.startsWith("Bearer ");
        if (!isBearer) return null;

        return authorization.substring(7);
    }
}
