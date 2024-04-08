package com.ninemensmorris.user.service;

import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.dto.UserResponseDto;
import com.ninemensmorris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponseDto getUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserId = authentication.getName();

        User user = userRepository.findByUserId(Long.parseLong(currentUserId));

        return UserResponseDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .imageUrl(user.getImageUrl())
                .role(user.getRole())
                .score(user.getScore())
                .build();
    }
}
