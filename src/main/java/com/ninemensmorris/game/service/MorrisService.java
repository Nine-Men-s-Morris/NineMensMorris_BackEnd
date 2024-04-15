package com.ninemensmorris.game.service;

import com.ninemensmorris.common.exception.CustomException;
import com.ninemensmorris.common.response.ErrorCode;
import com.ninemensmorris.common.response.MorrisResponse;
import com.ninemensmorris.common.response.MorrisResponse.ResponseType;
import com.ninemensmorris.common.response.MorrisResponseCode;
import com.ninemensmorris.game.domain.GameRoom;
import com.ninemensmorris.game.domain.MorrisStatus;
import com.ninemensmorris.game.dto.Morris.RemoveOpponentStoneRequestDto;
import com.ninemensmorris.game.dto.Morris.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.Morris.StonePlacementResponseDto;
import com.ninemensmorris.game.dto.Morris.WithdrawRequestDto;
import com.ninemensmorris.game.repository.GameRoomRepository;
import com.ninemensmorris.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MorrisService {

    private final UserService userService;
    private final GameRoomRepository gameRoomRepository;

    private static final String PLAYER_ONE_STONE = "BLACK";
    private static final String PLAYER_TWO_STONE = "WHITE";
    private static final String EMPTY_CELL = "EMPTY";

    private final Map<Long, String[]> gameBoards = new HashMap<>();
    private final Map<Long, Integer> gamePhases = new HashMap<>();
    private final Map<Long, String> playerStones = new HashMap<>();
    private final Map<Long, GameRoom> gameRooms = new HashMap<>();
    private final Map<Long, Integer> hostAddableStones = new HashMap<>();
    private final Map<Long, Integer> guestAddableStones = new HashMap<>();
    private final Map<Long, Integer> hostTotal = new HashMap<>();
    private final Map<Long, Integer> guestTotal = new HashMap<>();
    private final Map<Long, Long> currentTurns = new HashMap<>();

    public MorrisResponse<StonePlacementResponseDto> startGame(Long gameId) {
        Optional<GameRoom> optionalGameRoom = gameRoomRepository.findById(gameId);
        GameRoom gameRoom = optionalGameRoom.orElseThrow(() -> new CustomException(ErrorCode.GAME_ROOM_NOT_FOUND));

        String[] board = new String[24];
        Arrays.fill(board, EMPTY_CELL);
        gameBoards.put(gameId, board);
        gamePhases.put(gameId, 1);
        playerStones.put(gameId, PLAYER_ONE_STONE);
        gameRooms.put(gameId, gameRoom);
        hostAddableStones.put(gameId, 9);
        guestAddableStones.put(gameId, 9);
        hostTotal.put(gameId, 9);
        guestTotal.put(gameId, 9);
        currentTurns.put(gameId, gameRoom.getPlayerOneId());

        StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                .board(board)
                .hostId(gameRoom.getPlayerOneId())
                .guestId(gameRoom.getPlayerTwoId())
                .currentTurn(gameRoom.getPlayerOneId())
                .hostAddable(9)
                .guestAddable(9)
                .hostTotal(9)
                .guestTotal(9)
                .phase(1)
                .isRemoving(false)
                .status(MorrisStatus.Status.PLAYING)
                .winner(null)
                .loser(null)
                .build();

        return MorrisResponse.response(ResponseType.GAME_START, MorrisResponseCode.GAME_START, responseDto);
    }

    public MorrisResponse<StonePlacementResponseDto> placeStone(StonePlacementRequestDto placementRequest) {
        Long gameId = placementRequest.getGameId();
        String[] board = gameBoards.get(gameId);
        GameRoom gameRoom = gameRooms.get(gameId);
        int currentPhase = gamePhases.get(gameId);

        int initialPosition = placementRequest.getInitialPosition();
        int finalPosition = placementRequest.getFinalPosition();

        String currentPlayerStone = playerStones.get(gameId);

        if (currentPhase == 1) {
            placeStonePhaseOne(gameId, initialPosition, currentPlayerStone);
            decreaseAddableStones(gameId);
            if (checkRemovalConditions(gameId, board, initialPosition)) {
                StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                        .board(board)
                        .hostId(gameRoom.getPlayerOneId())
                        .guestId(gameRoom.getPlayerTwoId())
                        .currentTurn(currentTurns.get(gameId))
                        .hostAddable(hostAddableStones.get(gameId))
                        .guestAddable(guestAddableStones.get(gameId))
                        .hostTotal(hostTotal.get(gameId))
                        .guestTotal(guestTotal.get(gameId))
                        .phase(gamePhases.get(gameId))
                        .isRemoving(true)
                        .status(MorrisStatus.Status.PLAYING)
                        .winner(null)
                        .loser(null)
                        .build();

                return MorrisResponse.response(ResponseType.GAME_STATE_UPDATE, MorrisResponseCode.CANNOT_REMOVE, responseDto);
            } else {
                switchTurn(gameId, gameRoom);
            }
        } else if (currentPhase == 2) {
            placeStonePhaseTwo(gameId, initialPosition, finalPosition);
            if (checkRemovalConditions(gameId, board, finalPosition)) {
                StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                        .board(board)
                        .hostId(gameRoom.getPlayerOneId())
                        .guestId(gameRoom.getPlayerTwoId())
                        .currentTurn(currentTurns.get(gameId))
                        .hostAddable(hostAddableStones.get(gameId))
                        .guestAddable(guestAddableStones.get(gameId))
                        .hostTotal(hostTotal.get(gameId))
                        .guestTotal(guestTotal.get(gameId))
                        .phase(gamePhases.get(gameId))
                        .isRemoving(true)
                        .status(MorrisStatus.Status.PLAYING)
                        .winner(null)
                        .loser(null)
                        .build();

                return MorrisResponse.response(ResponseType.GAME_STATE_UPDATE, MorrisResponseCode.CANNOT_REMOVE, responseDto);
            } else {
                switchTurn(gameId, gameRoom);
            }
        }

        if (currentPhase == 2 && checkEndGameConditions(gameId, board, gameRoom)) {
            return handleMorrisResult(gameId);
        }

        StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                .board(board)
                .hostId(gameRoom.getPlayerOneId())
                .guestId(gameRoom.getPlayerTwoId())
                .currentTurn(currentTurns.get(gameId))
                .hostAddable(hostAddableStones.get(gameId))
                .guestAddable(guestAddableStones.get(gameId))
                .hostTotal(hostTotal.get(gameId))
                .guestTotal(guestTotal.get(gameId))
                .phase(gamePhases.get(gameId))
                .isRemoving(false)
                .status(MorrisStatus.Status.PLAYING)
                .winner(null)
                .loser(null)
                .build();

        return MorrisResponse.response(ResponseType.GAME_STATE_UPDATE, MorrisResponseCode.STONE_PLACEMENT_SUCCESS, responseDto);
    }

    public MorrisResponse<StonePlacementResponseDto> removeOpponentStone(RemoveOpponentStoneRequestDto requestDto) {
        Long gameId = requestDto.getGameId();
        int removePosition = requestDto.getRemovePosition();

        GameRoom gameRoom = gameRooms.get(gameId);
        String[] board = gameBoards.get(gameId);

        if (removePosition == 99) {
            switchTurn(gameId, gameRoom);

            StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                    .board(board)
                    .hostId(gameRoom.getPlayerOneId())
                    .guestId(gameRoom.getPlayerTwoId())
                    .currentTurn(currentTurns.get(gameId))
                    .hostAddable(hostAddableStones.get(gameId))
                    .guestAddable(guestAddableStones.get(gameId))
                    .hostTotal(hostTotal.get(gameId))
                    .guestTotal(guestTotal.get(gameId))
                    .phase(gamePhases.get(gameId))
                    .isRemoving(false)
                    .status(MorrisStatus.Status.PLAYING)
                    .winner(null)
                    .loser(null)
                    .build();

            return MorrisResponse.response(ResponseType.GAME_STATE_UPDATE, MorrisResponseCode.TURN_CHANGE, responseDto);
        }

        // 제거하려는 말이 연속된 3행 또는 3열인 경우 제거할 수 없음
        if (checkRowOrColumnTriples(gameId, board, removePosition)) {
            StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                    .board(board)
                    .hostId(gameRoom.getPlayerOneId())
                    .guestId(gameRoom.getPlayerTwoId())
                    .currentTurn(currentTurns.get(gameId))
                    .hostAddable(hostAddableStones.get(gameId))
                    .guestAddable(guestAddableStones.get(gameId))
                    .hostTotal(hostTotal.get(gameId))
                    .guestTotal(guestTotal.get(gameId))
                    .phase(gamePhases.get(gameId))
                    .isRemoving(false)
                    .status(MorrisStatus.Status.PLAYING)
                    .winner(null)
                    .loser(null)
                    .build();

            return MorrisResponse.response(ResponseType.GAME_STATE_UPDATE, MorrisResponseCode.CANNOT_REMOVE_ROW_COLUMN, responseDto);
        }

        board[removePosition] = EMPTY_CELL;

        String currentPlayerStone = playerStones.get(gameId);
        if (currentPlayerStone.equals(PLAYER_ONE_STONE)) {
            int guestTotalStones = guestTotal.get(gameId);
            guestTotal.put(gameId, guestTotalStones - 1);
        } else if (currentPlayerStone.equals(PLAYER_TWO_STONE)) {
            int hostTotalStones = hostTotal.get(gameId);
            hostTotal.put(gameId, hostTotalStones - 1);
        }

        if (checkEndGameConditions(gameId, board, gameRoom)) {
            return handleMorrisResult(gameId);
        }

        switchTurn(gameId, gameRoom);

        StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                .board(board)
                .hostId(gameRoom.getPlayerOneId())
                .guestId(gameRoom.getPlayerTwoId())
                .currentTurn(currentTurns.get(gameId))
                .hostAddable(hostAddableStones.get(gameId))
                .guestAddable(guestAddableStones.get(gameId))
                .hostTotal(hostTotal.get(gameId))
                .guestTotal(guestTotal.get(gameId))
                .phase(gamePhases.get(gameId))
                .isRemoving(false)
                .status(MorrisStatus.Status.PLAYING)
                .winner(null)
                .loser(null)
                .build();

        return MorrisResponse.response(ResponseType.GAME_STATE_UPDATE, MorrisResponseCode.STONE_REMOVAL_SUCCESS, responseDto);
    }

    public MorrisResponse<StonePlacementResponseDto> handleMorrisResult(Long gameId) {
        String[] board = gameBoards.get(gameId);
        GameRoom gameRoom = gameRooms.get(gameId);
        Long winnerId = determineWinner(gameId);

        if (winnerId != null) {
            Long loserId = (winnerId.equals(gameRoom.getPlayerOneId())) ? gameRoom.getPlayerTwoId() : gameRoom.getPlayerOneId();

            if (winnerId.equals(gameRoom.getPlayerOneId())) {
                userService.increaseScore(winnerId, 30);
                userService.decreaseScore(loserId, 20);
            } else {
                userService.increaseScore(loserId, 30);
                userService.decreaseScore(winnerId, 20);
            }

            gameRoomRepository.delete(gameRoom);

            StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                    .board(board)
                    .hostId(gameRoom.getPlayerOneId())
                    .guestId(gameRoom.getPlayerTwoId())
                    .currentTurn(currentTurns.get(gameId))
                    .hostAddable(hostAddableStones.get(gameId))
                    .guestAddable(guestAddableStones.get(gameId))
                    .hostTotal(hostTotal.get(gameId))
                    .guestTotal(guestTotal.get(gameId))
                    .phase(gamePhases.get(gameId))
                    .isRemoving(false)
                    .status(MorrisStatus.Status.FINISHED)
                    .winner(winnerId)
                    .loser(loserId)
                    .build();

            return MorrisResponse.response(ResponseType.GAME_OVER, MorrisResponseCode.GAME_OVER, responseDto);

        } else {
            return MorrisResponse.response(ResponseType.GAME_OVER, MorrisResponseCode.NO_WINNER, null);
        }
    }

    public MorrisResponse<StonePlacementResponseDto> withdraw(WithdrawRequestDto requestDto) {
        Long gameId = requestDto.getGameId();
        Long userId = requestDto.getUserId();
        GameRoom gameRoom = gameRooms.get(gameId);

        String[] board = gameBoards.get(gameId);
        Long winnerId = gameRoom.getPlayerOneId().equals(userId) ? gameRoom.getPlayerTwoId() : gameRoom.getPlayerOneId();
        Long loserId = userId;

        userService.increaseScore(winnerId, 20);
        userService.decreaseScore(loserId, 20);

        gameRoomRepository.delete(gameRoom);

        StonePlacementResponseDto responseDto = StonePlacementResponseDto.builder()
                .board(board)
                .hostId(gameRoom.getPlayerOneId())
                .guestId(gameRoom.getPlayerTwoId())
                .currentTurn(currentTurns.get(gameId))
                .hostAddable(hostAddableStones.get(gameId))
                .guestAddable(guestAddableStones.get(gameId))
                .hostTotal(hostTotal.get(gameId))
                .guestTotal(guestTotal.get(gameId))
                .phase(gamePhases.get(gameId))
                .isRemoving(false)
                .status(MorrisStatus.Status.FINISHED)
                .winner(winnerId)
                .loser(loserId)
                .build();

        return MorrisResponse.response(ResponseType.GAME_OVER, MorrisResponseCode.GAME_OVER, responseDto);
    }


    private void placeStonePhaseOne(Long gameId, int initialPosition, String currentPlayerStone) {
        String[] board = gameBoards.get(gameId);
        board[initialPosition] = currentPlayerStone;
    }

    private void placeStonePhaseTwo(Long gameId, int initialPosition, int finalPosition) {
        String[] board = gameBoards.get(gameId);
        String stone = board[initialPosition];
        board[initialPosition] = EMPTY_CELL;
        board[finalPosition] = stone;
    }

    private void switchTurn(Long gameId, GameRoom gameRoom) {
        Long nextTurn = (gameRoom.getPlayerOneId().equals(currentTurns.get(gameId))) ? gameRoom.getPlayerTwoId() : gameRoom.getPlayerOneId();
        currentTurns.put(gameId, nextTurn);
        playerStones.put(gameId, (nextTurn.equals(gameRoom.getPlayerOneId())) ? PLAYER_ONE_STONE : PLAYER_TWO_STONE);
    }

    private void decreaseAddableStones(Long gameId) {
        String currentPlayerStone = playerStones.get(gameId);
        int hostStonesLeft = hostAddableStones.get(gameId);
        int guestStonesLeft = guestAddableStones.get(gameId);

        if (currentPlayerStone.equals(PLAYER_ONE_STONE)) {
            hostStonesLeft--;
            hostAddableStones.put(gameId, hostStonesLeft);
        } else if (currentPlayerStone.equals(PLAYER_TWO_STONE)) {
            guestStonesLeft--;
            guestAddableStones.put(gameId, guestStonesLeft);
        }

        if (hostStonesLeft == 0 && guestStonesLeft == 0) {
            gamePhases.put(gameId, 2);
        }
    }

    /**
     *
     * 3연속 행, 열 체킹
     */
    private static final int[][] rowTriples = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {9, 10, 11},
            {12, 13, 14}, {15, 16, 17}, {18, 19, 20}, {21, 22, 23}
    };

    private static final int[][] columnTriples = {
            {0, 9, 21}, {3, 10, 18}, {6, 11, 15}, {1, 4, 7},
            {16, 19, 22}, {8, 12, 17}, {5, 13, 20}, {2, 14, 23}
    };

    private boolean checkRemovalConditions(Long gameId, String[] board, int position) {
        for (int[] triple : rowTriples) {
            if (Arrays.stream(triple).anyMatch(p -> p == position)) {
                String currentPlayerStone = playerStones.get(gameId);
                if (board[triple[0]].equals(currentPlayerStone) && board[triple[1]].equals(currentPlayerStone) && board[triple[2]].equals(currentPlayerStone)) {
                    return true;
                }
            }
        }

        for (int[] triple : columnTriples) {
            if (Arrays.stream(triple).anyMatch(p -> p == position)) {
                String currentPlayerStone = playerStones.get(gameId);
                if (board[triple[0]].equals(currentPlayerStone) && board[triple[1]].equals(currentPlayerStone) && board[triple[2]].equals(currentPlayerStone)) {
                    return true;
                }
            }
        }

        return false;
    }

    // 제거하려는 돌이 3연속 행, 열에 위치하여있는지 확인 (true 반환일 경우 제거할 수 없음)
    private boolean checkRowOrColumnTriples(Long gameId, String[] board, int removePosition) {
        String currentPlayerStone = playerStones.get(gameId);

        for (int[] triple : rowTriples) {
            if (Arrays.stream(triple).anyMatch(p -> p == removePosition)) {
                if (isAllOpponentStones(board, triple, currentPlayerStone)) {
                    return true;
                }
            }
        }

        for (int[] triple : columnTriples) {
            if (Arrays.stream(triple).anyMatch(p -> p == removePosition)) {
                if (isAllOpponentStones(board, triple, currentPlayerStone)) {
                    return true;
                }
            }
        }

        return false; // 제거 가능
    }

    private boolean isAllOpponentStones(String[] board, int[] triples, String currentPlayerStone) {
        for (int position : triples) {
            if (board[position].equals(currentPlayerStone) || board[position].equals(EMPTY_CELL)) {
                return false; // 제거 가능
            }
        }
        return true; // 제거 불가능
    }

    /**
     * 게임 승패 판단 체킹
     */

    private boolean checkEndGameConditions(Long gameId, String[] board, GameRoom gameRoom) {
        int playerOneRemainStones = hostTotal.get(gameId);
        int playerTwoRemainStones = guestTotal.get(gameId);

        boolean playerOneCanMove = checkPlayerCanMove(board, gameRoom, gameRoom.getPlayerOneId());
        boolean playerTwoCanMove = checkPlayerCanMove(board, gameRoom, gameRoom.getPlayerTwoId());

        if (!playerOneCanMove || playerOneRemainStones <= 2) {
            return true;
        } else if (!playerTwoCanMove || playerTwoRemainStones <= 2) {
            return true;
        }

        return false;
    }

    private Long determineWinner(Long gameId) {
        GameRoom gameRoom = gameRooms.get(gameId);
        String[] board = gameBoards.get(gameId);

        int playerOneRemainStones = hostTotal.get(gameId);
        int playerTwoRemainStones = guestTotal.get(gameId);

        boolean playerOneCanMove = checkPlayerCanMove(board, gameRoom, gameRoom.getPlayerOneId());
        boolean playerTwoCanMove = checkPlayerCanMove(board, gameRoom, gameRoom.getPlayerTwoId());

        if (!playerOneCanMove || playerOneRemainStones <= 2) {
            return gameRoom.getPlayerTwoId();
        } else if (!playerTwoCanMove || playerTwoRemainStones <= 2) {
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
                {11, 16}, {15, 17, 19}, {12, 16}, {10, 19},
                {16, 18, 20, 22}, {13, 19}, {9, 22}, {19, 21, 23},
                {14, 22}
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
}
