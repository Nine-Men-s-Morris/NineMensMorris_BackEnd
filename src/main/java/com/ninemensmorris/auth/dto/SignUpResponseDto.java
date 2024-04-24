package com.ninemensmorris.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SignUpResponseDto {

    private String loginId;
    private String password;
    private String nickname;
    private String imageUrl;
    private String role;
    private int score;
}
