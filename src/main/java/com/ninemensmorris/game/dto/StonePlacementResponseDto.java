package com.ninemensmorris.game.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class StonePlacementResponseDto {

    private boolean success;
    private String message;
}
