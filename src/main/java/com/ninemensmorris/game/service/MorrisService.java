package com.ninemensmorris.game.service;

import com.ninemensmorris.game.domain.GameRoom;
import com.ninemensmorris.game.dto.MorrisResultDto;
import com.ninemensmorris.game.dto.RemoveOpponentStoneRequestDto;
import com.ninemensmorris.game.dto.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.StonePlacementResponseDto;
import com.ninemensmorris.game.repository.GameRoomRepository;
import com.ninemensmorris.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MorrisService {

    private final UserService userService;
    private GameRoomRepository gameRoomRepository;

    private static final String PLAYER_ONE_STONE = "BLACK";
    private static final String PLAYER_TWO_STONE = "WHITE";
    private static final String EMPTY_CELL = "EMPTY";

    private final Map<Long, String[]> gameBoards = new HashMap<>();
    private final Map<Long, Boolean> gamePhaseTwo = new HashMap<>();
    private final Map<Long, String> playerStones = new HashMap<>();

    private int countEmptyCells(String[] board) {
        int count = 0;
        for (String cell : board) {
            if (Objects.equals(cell, EMPTY_CELL)) {
                count++;
            }
        }
        return count;
    }

    public StonePlacementResponseDto startGame(Long gameId) {
        String[] board = new String[24];
        for (int i = 0; i < 24; i++) {
            board[i] = EMPTY_CELL;
        }
        gameBoards.put(gameId, board);
        gamePhaseTwo.put(gameId, false);
        playerStones.put(gameId, PLAYER_ONE_STONE);

        return new StonePlacementResponseDto(true, "게임을 시작합니다.", board);
    }

    public StonePlacementResponseDto placeStone(StonePlacementRequestDto placementRequest) {
        Long gameId = placementRequest.getGameId();
        String[] board = gameBoards.get(gameId);
        if (board == null) {
            return new StonePlacementResponseDto(false, "해당 게임을 찾을 수 없습니다.", null);
        }

        int initialPosition = placementRequest.getInitialPosition();
        int finalPosition = placementRequest.getFinalPosition();

        boolean phaseTwo = gamePhaseTwo.get(gameId);
        String currentPlayerStone = playerStones.get(gameId);
        if (!phaseTwo && initialPosition >= 0 && initialPosition < 24 && finalPosition == 99 && board[initialPosition].equals(EMPTY_CELL)) {
            placeStonePhaseOne(gameId, initialPosition, currentPlayerStone);
        } else if (phaseTwo && initialPosition >= 0 && initialPosition < 24 && finalPosition >= 0 && finalPosition < 24 && board[initialPosition].equals(currentPlayerStone) && board[finalPosition].equals(EMPTY_CELL)) {
            placeStonePhaseTwo(gameId, initialPosition, finalPosition);
        } else {
            return new StonePlacementResponseDto(false, "유효하지 않은 위치입니다.", null);
        }

        playerStones.put(gameId, currentPlayerStone.equals(PLAYER_ONE_STONE) ? PLAYER_TWO_STONE : PLAYER_ONE_STONE);

        return new StonePlacementResponseDto(true, "돌을 성공적으로 놓았습니다.", board);
    }

    private void placeStonePhaseOne(Long gameId, int initialPosition, String currentPlayerStone) {
        String[] board = gameBoards.get(gameId);
        board[initialPosition] = currentPlayerStone;

        int emptyCellCount = countEmptyCells(board);
        if (emptyCellCount <= 15) {
            gamePhaseTwo.put(gameId, true);
        }
    }

    private void placeStonePhaseTwo(Long gameId, int initialPosition, int finalPosition) {
        String[] board = gameBoards.get(gameId);
        String stone = board[initialPosition];
        board[initialPosition] = EMPTY_CELL;
        board[finalPosition] = stone;
    }

    public StonePlacementResponseDto removeOpponentStone(RemoveOpponentStoneRequestDto requestDto) {
        Long gameId = requestDto.getGameId();
        int removePosition = requestDto.getRemovePosition();

        String[] board = gameBoards.get(gameId);

        if (checkRemovalConditions(board)) {
            board[removePosition] = EMPTY_CELL;
            return new StonePlacementResponseDto(true, "돌을 성공적으로 제거하였습니다.", board);
        } else {
            return new StonePlacementResponseDto(false, "해당 위치에 있는 돌을 제거할 수 없습니다.", null);
        }
    }

    private static final int[][] rowTriples = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {9, 10, 11},
            {12, 13, 14}, {15, 16, 17}, {18, 19, 20}, {21, 22, 23}
    };

    private static final int[][] columnTriples = {
            {0, 9, 21}, {3, 10, 18}, {6, 11, 15}, {1, 4, 7},
            {16, 19, 22}, {8, 12, 17}, {5, 13, 20}, {2, 14, 23}
    };

    private boolean checkRemovalConditions(String[] board) {
        if (checkHorizontalThreeInARow(board)) {
            return true;
        }

        if (checkVerticalThreeInARow(board)) {
            return true;
        }

        return false;
    }

    private boolean checkHorizontalThreeInARow(String[] board) {
        for (int[] triple : rowTriples) {
            String stone = board[triple[0]];
            if (!stone.equals(EMPTY_CELL) && board[triple[1]].equals(stone) && board[triple[2]].equals(stone)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkVerticalThreeInARow(String[] board) {
        for (int[] triple : columnTriples) {
            String stone = board[triple[0]];
            if (!stone.equals(EMPTY_CELL) && board[triple[1]].equals(stone) && board[triple[2]].equals(stone)) {
                return true;
            }
        }
        return false;
    }

    public StonePlacementResponseDto handleMorrisResult(MorrisResultDto morrisResultDto) {
        Long gameId = morrisResultDto.getGameId();
        Long winnerId = morrisResultDto.getWinnerId();
        GameRoom gameRoom = gameRoomRepository.findById(gameId).get();

        Long winner = determineWinner(gameId);
        Long loserId = (winnerId.equals(gameRoom.getPlayerOneId())) ? gameRoom.getPlayerTwoId() : gameRoom.getPlayerOneId();
        if (winner != null) {
            if (winner.equals(winnerId)) {
                userService.increaseScore(winnerId);
                userService.decreaseScore(loserId);
                gameRoomRepository.delete(gameRoom);
                return new StonePlacementResponseDto(true, "게임에서 승리하였습니다.", null);
            } else {
                userService.increaseScore(loserId);
                userService.decreaseScore(winnerId);
                gameRoomRepository.delete(gameRoom);
                return new StonePlacementResponseDto(true, "게임에서 패배하였습니다.", null);
            }
        } else {
        return new StonePlacementResponseDto(false, "게임이 아직 종료되지 않았습니다.(로직 오류)", null);
        }
    }

    private Long determineWinner(Long gameId) {
        GameRoom gameRoom = gameRoomRepository.findById(gameId).get();
        String[] board = gameBoards.get(gameId);

        boolean playerOneCanMove = checkPlayerCanMove(board, gameRoom, gameRoom.getPlayerOneId());
        boolean playerTwoCanMove = checkPlayerCanMove(board, gameRoom, gameRoom.getPlayerTwoId());

        if (!playerOneCanMove) {
            return gameRoom.getPlayerTwoId();
        } else if (!playerTwoCanMove) {
            return gameRoom.getPlayerOneId();
        }

        int playerOneRemainStones = countRemainingStones(board, gameRoom, gameRoom.getPlayerOneId());
        int playerTwoRemainStones = countRemainingStones(board, gameRoom, gameRoom.getPlayerTwoId());

        if (playerOneRemainStones <= 2) {
            return gameRoom.getPlayerTwoId();
        } else if (playerTwoRemainStones <= 2) {
            return gameRoom.getPlayerOneId();
        }

        return null;
    }

    private boolean checkPlayerCanMove(String[] board, GameRoom gameRoom, Long userId) {
        String playerStone = (userId.equals(gameRoom.getPlayerOneId())) ? PLAYER_ONE_STONE : PLAYER_TWO_STONE;
        int[][] adjacentIndexes = {
                {1, 9}, {0, 2, 4}, {1, 14},
                {4, 10}, {1, 3, 5, 7}, {4, 13},
                {7, 11}, {4, 6, 8}, {7, 12},
                {0, 10, 21}, {3, 9, 11, 18}, {6, 10, 15},
                {8, 13, 17}, {5, 12, 14, 20}, {2, 13, 23},
                {11, 16}, {15, 17, 19}, {12, 16},
                {16, 18, 20, 22}, {13, 19}, {10, 21, 23},
                {19, 22}, {9, 22}
        };

        for (int i = 0; i < board.length; i++) {
            if (board[i].equals(playerStone)) {
                for (int adjacentIndex : adjacentIndexes[i]) {
                    if (board[adjacentIndex].equals(EMPTY_CELL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int countRemainingStones(String[] board, GameRoom gameRoom, Long userId) {
        String playerStone = (userId.equals(gameRoom.getPlayerOneId())) ? PLAYER_ONE_STONE : PLAYER_TWO_STONE;
        int stoneCount = 0;

        for (String cell : board) {
            if (cell.equals(playerStone)) {
                stoneCount++;
            }
        }
        return stoneCount;
    }
}
