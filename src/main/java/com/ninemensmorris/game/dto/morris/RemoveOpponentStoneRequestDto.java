package com.ninemensmorris.game.dto.morris;

import lombok.Getter;

@Getter
public class RemoveOpponentStoneRequestDto {

    private Long gameId;
    private int removePosition;
}
