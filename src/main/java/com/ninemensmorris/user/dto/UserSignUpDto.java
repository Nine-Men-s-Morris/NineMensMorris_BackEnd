package com.ninemensmorris.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserSignUpDto {

    private String email;
    private String nickname;
    private String imageUrl;
}
