package com.ninemensmorris.game.service;

import com.ninemensmorris.game.dto.RemoveOpponentStoneRequestDto;
import com.ninemensmorris.game.dto.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.StonePlacementResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MorrisService {

    private static final char PLAYER_ONE_STONE = '흑';
    private static final char PLAYER_TWO_STONE = '백';
    private static final char EMPTY_CELL = '-';

    private final Map<Long, char[]> gameBoards = new HashMap<>();
    private final Map<Long, Boolean> gamePhaseTwo = new HashMap<>();
    private final Map<Long, Boolean> playerOneTurns = new HashMap<>();

    private int countEmptyCells(char[] board) {
        int count = 0;
        for (char cell : board) {
            if (cell == EMPTY_CELL) {
                count++;
            }
        }
        return count;
    }

    public void startGame(Long gameId) {
        char[] board = new char[24];
        for (int i = 0; i < 24; i++) {
            board[i] = EMPTY_CELL;
        }
        gameBoards.put(gameId, board);
        gamePhaseTwo.put(gameId, false);
        playerOneTurns.put(gameId, true);
    }

    public StonePlacementResponseDto placeStone(StonePlacementRequestDto placementRequest) {
        Long gameId = placementRequest.getGameId();
        char[] board = gameBoards.get(gameId);
        if (board == null) {
            return new StonePlacementResponseDto(false, "해당 게임을 찾을 수 없습니다.");
        }

        int initialPosition = placementRequest.getInitialPosition();
        int finalPosition = placementRequest.getFinalPosition();

        boolean phaseTwo = gamePhaseTwo.get(gameId);
        if (!phaseTwo && initialPosition >= 0 && initialPosition < 24 && finalPosition == 99 && board[initialPosition] == EMPTY_CELL) {
            return placeStonePhaseOne(gameId, initialPosition);
        } else if (phaseTwo && initialPosition >= 0 && initialPosition < 24 && finalPosition >= 0 && finalPosition < 24 && board[initialPosition] != EMPTY_CELL && board[finalPosition] == EMPTY_CELL) {
            return placeStonePhaseTwo(gameId, initialPosition, finalPosition);
        }

        return new StonePlacementResponseDto(false, "유효하지 않은 위치입니다.");
    }

    private StonePlacementResponseDto placeStonePhaseOne(Long gameId, int initialPosition) {
        char[] board = gameBoards.get(gameId);
        char stone = playerOneTurns.get(gameId) ? PLAYER_ONE_STONE : PLAYER_TWO_STONE;
        board[initialPosition] = stone;

        int emptyCellCount = countEmptyCells(board);
        if (emptyCellCount <= 15) {
            gamePhaseTwo.put(gameId, true);
        }

        playerOneTurns.put(gameId, !playerOneTurns.get(gameId));

        if (gamePhaseTwo.get(gameId)) {
            return new StonePlacementResponseDto(true, "2페이즈로 전환되었습니다.");
        } else {
            return new StonePlacementResponseDto(true, "돌을 성공적으로 놓았습니다.");
        }
    }

    private StonePlacementResponseDto placeStonePhaseTwo(Long gameId, int initialPosition, int finalPosition) {
        char[] board = gameBoards.get(gameId);
        char stone = board[initialPosition];
        board[initialPosition] = EMPTY_CELL;
        board[finalPosition] = stone;

        return new StonePlacementResponseDto(true, "돌을 성공적으로 옮겼습니다.");
    }

    public StonePlacementResponseDto removeOpponentStone(RemoveOpponentStoneRequestDto requestDto) {
        return new StonePlacementResponseDto(true, "돌을 성공적으로 제거하였습니다.");
    }
}
