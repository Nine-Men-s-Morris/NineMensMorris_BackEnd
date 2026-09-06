package com.ninemensmorris.auth.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

// 인증 성공 후 식별자만 들고 다니는 최소 구현
// 기존에는 getAttributes / getAuthorities 가 null 을 반환해 OAuth2User 계약을 어겼음
public record CustomOAuth2User(long userId) implements OAuth2User {

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
