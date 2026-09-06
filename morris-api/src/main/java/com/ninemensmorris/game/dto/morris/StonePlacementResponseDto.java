package com.ninemensmorris.game.dto.morris;

import com.ninemensmorris.core.game.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class StonePlacementResponseDto {

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
    private GameStatus status;
    private Long winner;
    private Long loser;
}
