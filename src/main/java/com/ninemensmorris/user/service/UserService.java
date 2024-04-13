package com.ninemensmorris.user.service;

import com.ninemensmorris.game.dto.GameRoomDto;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.dto.UserRankDto;
import com.ninemensmorris.user.dto.UserResponseDto;
import com.ninemensmorris.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EntityManager em;

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

    public List<UserRankDto> getUserRanks() {
        List<User> users = em.createQuery("SELECT u FROM User u ORDER BY u.score DESC", User.class)
                .getResultList();

        return users.stream()
                .map(UserRankDto::new)
                .collect(Collectors.toList());
    }

    public void increaseScore(Long userId) {
        User user = userRepository.findByUserId(userId);
        user.setScore(user.getScore() + 30);
        userRepository.save(user);
    }

    public void decreaseScore(Long userId) {
        User user = userRepository.findByUserId(userId);
        user.setScore(user.getScore() - 20);
        userRepository.save(user);
    }
}
