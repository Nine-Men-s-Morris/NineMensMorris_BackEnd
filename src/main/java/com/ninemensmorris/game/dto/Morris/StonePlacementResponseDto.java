package com.ninemensmorris.game.dto.Morris;

import com.ninemensmorris.game.domain.MorrisStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
public class StonePlacementResponseDto {

    private String message;
    private String[] board;
    private Long hostId;
    private Long guestId;
    private Long currentTurn;
    private int hostAddable;
    private int guestAddable;
    private int hostTotal;
    private int guestTotal;
    private int phase;
    private boolean isRemoving;
    private MorrisStatus.Status status;
    private Long winner;
    private Long loser;
}