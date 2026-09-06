package com.ninemensmorris.auth.service;

import com.ninemensmorris.user.domain.CustomOAuth2User;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String oauthClientName = userRequest.getClientRegistration().getClientName();

        // kakao 외의 프로바이더를 받으면 userId 가 null 인 채로 흘러가
        // getName() 이 문자열 "null" 을 반환하고 한참 뒤에 NumberFormatException 으로 터졌다
        if (!oauthClientName.equals("kakao")) {
            throw new OAuth2AuthenticationException("지원하지 않는 OAuth2 프로바이더: " + oauthClientName);
        }

        Long userId = Long.parseLong(oAuth2User.getAttribute("id").toString());
        if (userRepository.findByUserId(userId) == null) {
            createUserFromOAuth2User(oAuth2User, userId);
        }

        return new CustomOAuth2User(userId);
    }

    private void createUserFromOAuth2User(OAuth2User oAuth2User, Long userId) {
        Map<String, Object> properties = oAuth2User.getAttribute("properties");
        Map<String, Object> account = oAuth2User.getAttribute("kakao_account");

        String nickname = (String) properties.get("nickname");
        String email = (String) account.get("email");
        String profileImg = (String) properties.get("profile_image");

        User user = User.builder()
                .userId(userId)
                .email(email)
                .nickname(nickname)
                .imageUrl(profileImg)
                .role("USER")
                .score(0)
                .build();
        userRepository.save(user);
    }
}
