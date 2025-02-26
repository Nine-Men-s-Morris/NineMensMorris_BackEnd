package com.ninemensmorris.security.handler;

import com.ninemensmorris.security.provider.JwtProvider;
import com.ninemensmorris.user.domain.CustomOAuth2User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;

    @Value("${ACCESS_TOKEN_EXPIRATION}")
    private Long accessTokenExpiration;

    @Value("${DOMAIN}")
    private String domainUrl;

    @Override
    public void onAuthenticationSuccess (HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String userId = oAuth2User.getName();
        String accessToken = jwtProvider.generateAccessToken(Long.parseLong(userId));

        Cookie accessTokenCookie = new Cookie("access_token", accessToken);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(Math.toIntExact(accessTokenExpiration));
        accessTokenCookie.setHttpOnly(true);
        //accessTokenCookie.setSecure(true); // 240404 ldhbenecia | https 설정 이후 사용
        response.addCookie(accessTokenCookie);

        response.addHeader("Authorization", "Bearer " + accessToken);

        response.sendRedirect(domainUrl);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
