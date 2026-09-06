package com.ninemensmorris.auth.service;

import com.ninemensmorris.auth.domain.CustomOAuth2User;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String KAKAO = "kakao";

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // getClientName 은 표시 이름이라 설정에서 바뀔 수 있음. 등록 ID 로 판별함
        String provider = userRequest.getClientRegistration().getRegistrationId();
        if (!KAKAO.equals(provider)) {
            throw new OAuth2AuthenticationException("지원하지 않는 OAuth2 프로바이더: " + provider);
        }

        long kakaoId = Long.parseLong(oAuth2User.getAttribute("id").toString());
        userRepository.findById(kakaoId).orElseGet(() -> userRepository.save(toUser(oAuth2User, kakaoId)));

        return new CustomOAuth2User(kakaoId);
    }

    private User toUser(OAuth2User oAuth2User, long kakaoId) {
        Map<String, Object> properties = oAuth2User.getAttribute("properties");
        Map<String, Object> account = oAuth2User.getAttribute("kakao_account");

        String nickname = properties == null ? null : (String) properties.get("nickname");
        String profileImage = properties == null ? null : (String) properties.get("profile_image");
        // 이메일은 동의 항목이라 없을 수 있음
        String email = account == null ? null : (String) account.get("email");

        return User.ofKakao(kakaoId, email, nickname == null ? "플레이어" + kakaoId : nickname, profileImage);
    }
}
