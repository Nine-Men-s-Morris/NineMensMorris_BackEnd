package com.ninemensmorris.user.service;

import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.dto.UserSignUpDto;
import com.ninemensmorris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void signUp(UserSignUpDto userSignUpDto) {

        String email = userSignUpDto.getEmail();
        User existingUser = userRepository.findByEmail(email);
        if (existingUser != null) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        User user = User.builder()
                .email(userSignUpDto.getEmail())
                .nickname(userSignUpDto.getNickname())
                .imageUrl(userSignUpDto.getImageUrl())
                .build();

        userRepository.save(user);
    }
}
