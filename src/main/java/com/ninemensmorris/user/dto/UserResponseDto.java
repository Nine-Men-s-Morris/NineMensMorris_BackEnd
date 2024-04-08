package com.ninemensmorris.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserResponseDto {

    private Long userId;
    private String email;
    private String nickname;
    private String imageUrl;
    private String role;
    private int score;
}
