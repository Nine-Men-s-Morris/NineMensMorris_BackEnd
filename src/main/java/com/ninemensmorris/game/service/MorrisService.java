package com.ninemensmorris.game.service;

import com.ninemensmorris.game.domain.MorrisStatus;
import com.ninemensmorris.game.dto.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.StonePlacementResponseDto;
import org.springframework.stereotype.Service;

@Service
public class MorrisService {

    private static final char PLAYER_ONE_STONE = '흑';
    private static final char PLAYER_TWO_STONE = '백';
    private static final char EMPTY_CELL = '-';

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

    private int countEmptyCells() {
        int count = 0;
        for (char cell : board) {
            if (cell == EMPTY_CELL) {
                count++;
            }
        }
        return count;
    }

    public StonePlacementResponseDto placeStone(StonePlacementRequestDto placementRequest) {
        int initialPosition = placementRequest.getInitialPosition();
        int finalPosition = placementRequest.getFinalPosition();

        if (!phaseTwo && initialPosition >= 0 && initialPosition < 24 && finalPosition == 99 && board[initialPosition] == EMPTY_CELL) {
            return placeStonePhaseOne(initialPosition);
        } else if (phaseTwo && initialPosition >= 0 && initialPosition < 24 && finalPosition >= 0 && finalPosition < 24 && board[initialPosition] != EMPTY_CELL && board[finalPosition] == EMPTY_CELL) {
            return placeStonePhaseTwo(initialPosition, finalPosition);
        }

        return new StonePlacementResponseDto(false, "유효하지 않은 위치입니다.");
    }

    private StonePlacementResponseDto placeStonePhaseOne(int initialPosition) {
        char stone = playerOneTurn ? PLAYER_ONE_STONE : PLAYER_TWO_STONE;
        board[initialPosition] = stone;

        int emptyCellCount = countEmptyCells();
        if (emptyCellCount <= 15) {
            phaseTwo = true;
        }

        playerOneTurn = !playerOneTurn;

        if (phaseTwo) {
            return new StonePlacementResponseDto(true, "2페이즈로 전환되었습니다.");
        } else {
            return new StonePlacementResponseDto(true, "돌을 성공적으로 놓았습니다.");
        }
    }

    private StonePlacementResponseDto placeStonePhaseTwo(int initialPosition, int finalPosition) {
        char stone = board[initialPosition];
        board[initialPosition] = EMPTY_CELL;
        board[finalPosition] = stone;

        removeOpponentStone(finalPosition);

        return new StonePlacementResponseDto(true, "돌을 성공적으로 옮겼습니다.");
    }

    private void removeOpponentStone(int position) {
        // 상대방 돌을 제거하는 로직
    }


    public MorrisStatus getGameStatus() {
        return new MorrisStatus();
    }

}
