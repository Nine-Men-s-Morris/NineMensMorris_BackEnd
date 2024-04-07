package com.ninemensmorris.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class CreateGameResponseDto {

    private String roomTitle;
    private String host;
}
