package com.ninemensmorris.game.dto;

import lombok.Getter;

@Getter
public class StonePlacementRequestDto {

    private Long gameId;
    private int initialPosition;
    private int finalPosition;
}
