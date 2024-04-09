package com.ninemensmorris.game.dto;

import lombok.Getter;

@Getter
public class RemoveOpponentStoneRequestDto {

    private Long gameId;
    private int removePosition;
}
