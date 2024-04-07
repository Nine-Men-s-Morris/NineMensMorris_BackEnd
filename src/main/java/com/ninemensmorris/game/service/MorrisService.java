package com.ninemensmorris.game.service;

import com.ninemensmorris.game.domain.MorrisStatus;
import com.ninemensmorris.game.dto.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.StonePlacementResponseDto;
import org.springframework.stereotype.Service;

@Service
public class MorrisService {

    private static final char PLAYER_ONE_STONE = 'O'; // 흑
    private static final char PLAYER_TWO_STONE = 'X'; // 백
    private static final char EMPTY_CELL = '-'; // 없음

    private final char[] board = new char[24];
    private boolean playerOneTurn;

    private boolean phaseTwo;

    public MorrisService() {
        initializeBoard();
        playerOneTurn = true;
        phaseTwo = false;
    }

    private void initializeBoard() {
        for (int i = 0; i < 24; i++) {
            board[i] = EMPTY_CELL;
        }
    }

    public StonePlacementResponseDto placeStone(StonePlacementRequestDto placementRequest) {
        int initialPosition = placementRequest.getInitialPosition();
        int finalPosition = placementRequest.getFinalPosition();

        if (initialPosition >= 0 && initialPosition < 24 && board[initialPosition] == EMPTY_CELL) {
            char stone = playerOneTurn ? PLAYER_ONE_STONE : PLAYER_TWO_STONE;
            board[initialPosition] = stone;
            playerOneTurn = !playerOneTurn;
            return new StonePlacementResponseDto(true, "돌을 성공적으로 놓았습니다.");
        } else if (finalPosition >= 0 && finalPosition < 24 && board[finalPosition] == EMPTY_CELL) {
            char stone = board[initialPosition];
            board[initialPosition] = EMPTY_CELL;
            board[finalPosition] = stone;
            return new StonePlacementResponseDto(true, "돌을 성공적으로 옮겼습니다.");
        }

        return new StonePlacementResponseDto(false, "유효하지 않은 위치입니다.");
    }

    public MorrisStatus getGameStatus() {
        return new MorrisStatus();
    }

}
