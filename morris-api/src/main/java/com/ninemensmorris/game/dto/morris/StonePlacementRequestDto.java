package com.ninemensmorris.game.dto.morris;

import lombok.Getter;

@Getter
public class StonePlacementRequestDto {

    private Long gameId;
    private int initialPosition;
    private int finalPosition;
}
