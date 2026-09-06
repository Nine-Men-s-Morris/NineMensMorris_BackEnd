package com.ninemensmorris.game.dto.response;

import com.ninemensmorris.core.board.Board;
import com.ninemensmorris.core.board.Stone;
import com.ninemensmorris.core.game.GameStatus;
import com.ninemensmorris.core.game.MorrisGame;
import com.ninemensmorris.core.game.Phase;
import com.ninemensmorris.game.domain.Room;

// 판 상태 전체. 클라이언트는 이것만 보고 화면을 그린다
//
// 흑/백 기준으로 내려준다. 기존에는 host/guest 와 playerOne/playerTwo 가 뒤섞여 있었고
// 방장이 항상 흑이라는 전제가 코드에 박혀 있어 선공을 바꿀 수 없었다
public record GameStateResponse(
        String[] board,
        long blackId,
        long whiteId,
        long currentTurnId,
        int blackInHand,
        int whiteInHand,
        int blackOnBoard,
        int whiteOnBoard,
        Phase blackPhase,
        Phase whitePhase,
        boolean awaitingRemoval,
        GameStatus status,
        Long winnerId,
        Long loserId,
        String endReason) {

    public static GameStateResponse of(Room room, MorrisGame game) {
        Board board = game.board();
        String[] cells = new String[Board.POINTS];
        for (int point = 0; point < Board.POINTS; point++) {
            Stone stone = board.stoneAt(point);
            cells[point] = stone == null ? "EMPTY" : stone.name();
        }

        Long winnerId = null;
        Long loserId = null;
        String endReason = null;
        if (game.outcome() != null) {
            endReason = game.outcome().reason().name();
            Stone winner = game.outcome().winner();
            if (winner != null) {
                winnerId = room.userIdOf(winner);
                loserId = room.userIdOf(winner.opponent());
            }
        }

        return new GameStateResponse(
                cells,
                room.blackId(),
                room.whiteId(),
                room.userIdOf(game.currentTurn()),
                game.inHand(Stone.BLACK),
                game.inHand(Stone.WHITE),
                game.onBoard(Stone.BLACK),
                game.onBoard(Stone.WHITE),
                game.phaseOf(Stone.BLACK),
                game.phaseOf(Stone.WHITE),
                game.awaitingRemoval(),
                game.status(),
                winnerId,
                loserId,
                endReason);
    }
}
