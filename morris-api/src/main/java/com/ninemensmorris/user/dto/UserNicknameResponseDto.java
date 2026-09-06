package com.ninemensmorris.user.dto;

import lombok.Getter;

@Getter
public class UserNicknameResponseDto {

    private String nickname;

    public UserNicknameResponseDto(String nickname) {
        this.nickname = nickname;
    }
}
