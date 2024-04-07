package com.ninemensmorris.game.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGameResponseDto {

    private String roomTitle;
    private String host;
}
