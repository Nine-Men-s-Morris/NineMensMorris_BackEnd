package com.ninemensmorris.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String oauthClientName = userRequest.getClientRegistration().getClientName();

        try {
            System.out.println(new ObjectMapper().writeValueAsString(oAuth2User.getAttributes()));
        } catch (Exception exception) {
            log.error("Failed to oAuth2User: {}", exception.getMessage());
        }

        if (oauthClientName.equals("kakao")) {
            String idString = oAuth2User.getAttribute("id").toString();
            Long id = Long.parseLong(idString);
            User user = userRepository.findByProviderId(id);
            if (user == null) {
                user = createUserFromOAuth2User(oAuth2User, id);
            }
        }

        return oAuth2User;
    }

    private User createUserFromOAuth2User(OAuth2User oAuth2User, Long userId) {

        Map<String, Object> properties = oAuth2User.getAttribute("properties");
        Map<String, Object> account = oAuth2User.getAttribute("kakao_account");

        String nickname = (String) properties.get("nickname") ;
        String email = (String) account.get("email");
        String profileImg = (String) properties.get("profile_image");

        Optional<User> optionalUser = Optional.ofNullable(userRepository.findByProviderId(userId));
        if (optionalUser.isPresent()) {
            return optionalUser.get();
        } else {
            User user = User.builder()
                    .providerId(userId)
                    .email(email)
                    .nickname(nickname)
                    .imageUrl(profileImg)
                    .score(0)
                    .build();
            return userRepository.save(user);
        }
    }
}
