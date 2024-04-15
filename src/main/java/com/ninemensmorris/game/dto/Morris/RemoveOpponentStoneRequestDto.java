package com.ninemensmorris.game.dto.Morris;

import lombok.Getter;

@Getter
public class RemoveOpponentStoneRequestDto {

    private Long gameId;
    private int removePosition;
}
