package com.ninemensmorris.user.dto;

import com.ninemensmorris.user.domain.User;
import lombok.Getter;

@Getter
public class UserRankDto {

    private String nickname;
    private int score;
    private String imageUrl;

    public UserRankDto(User user) {
        this.nickname = user.getNickname();
        this.score = user.getScore();
        this.imageUrl = user.getImageUrl();
    }
}
