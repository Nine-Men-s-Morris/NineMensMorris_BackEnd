package com.ninemensmorris.auth.service;

import com.ninemensmorris.auth.domain.MorrisUser;
import com.ninemensmorris.auth.dto.SignUpRequestDto;
import com.ninemensmorris.auth.dto.SignUpResponseDto;
import com.ninemensmorris.auth.repository.MorrisUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final MorrisUserRepository morrisUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEFAULT_PROFILE_IMAGE}")
    private String defaultProfileImage;

    public SignUpResponseDto signUp(SignUpRequestDto requestDto) {
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        MorrisUser user = MorrisUser.builder()
                .loginId(requestDto.getLoginId())
                .password(encodedPassword)
                .nickname(requestDto.getNickname())
                .imageUrl(defaultProfileImage)
                .role("USER")
                .score(0)
                .build();

        MorrisUser savedUser = morrisUserRepository.save(user);

        return SignUpResponseDto.builder()
                .loginId(savedUser.getLoginId())
                .password(savedUser.getPassword())
                .nickname(savedUser.getNickname())
                .imageUrl(savedUser.getImageUrl())
                .role(savedUser.getRole())
                .score(savedUser.getScore())
                .build();
    }
}
