package com.ninemensmorris.game.dto.Morris;

import lombok.Getter;

@Getter
public class StonePlacementRequestDto {

    private Long gameId;
    private Long userId;
    private int initialPosition;
    private int finalPosition;
}
