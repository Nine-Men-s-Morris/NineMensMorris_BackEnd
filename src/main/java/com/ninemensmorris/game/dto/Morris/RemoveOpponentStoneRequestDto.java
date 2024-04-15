package com.ninemensmorris.game.dto.Morris;

import lombok.Getter;

@Getter
public class RemoveOpponentStoneRequestDto {

    private Long gameId;
    private Long userId;
    private int removePosition;
}
