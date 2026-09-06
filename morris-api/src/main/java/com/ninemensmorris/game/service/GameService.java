package com.ninemensmorris.game.service;

import com.ninemensmorris.common.exception.CustomException;
import com.ninemensmorris.common.response.ErrorCode;
import com.ninemensmorris.core.board.Stone;
import com.ninemensmorris.core.game.MorrisGame;
import com.ninemensmorris.core.game.Outcome;
import com.ninemensmorris.core.move.Move;
import com.ninemensmorris.core.move.MoveResult;
import com.ninemensmorris.core.move.RejectReason;
import com.ninemensmorris.game.command.RoomCommand;
import com.ninemensmorris.game.domain.Room;
import com.ninemensmorris.game.domain.RoomRegistry;
import com.ninemensmorris.game.dto.response.GameStateResponse;
import com.ninemensmorris.game.dto.response.MoveRejectedResponse;
import com.ninemensmorris.game.dto.response.RoomEvent;
import com.ninemensmorris.game.dto.response.RoomEventType;
import com.ninemensmorris.match.service.MatchResultService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 게임 진행. 규칙 판단은 전부 MorrisGame 이 하고 여기서는 방과 사용자에 연결만 함
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final RoomRegistry rooms;
    private final MatchResultService matchResultService;

    // 선공 방식 변경. 방장만, 시작 전에만
    // 방을 다시 만들지 않고 바꿀 수 있어야 해서 생성 시점이 아니라 여기서 정한다
    public RoomEvent changeFirstMoveRule(RoomCommand.ChangeFirstMoveRule command) {
        return inRoom(command.roomId(), room -> {
            requireHost(room, command.actorId());
            if (room.isPlaying()) {
                throw new CustomException(ErrorCode.GAME_ALREADY_STARTED);
            }
            room.changeFirstMoveRule(command.rule());
            return RoomEvent.by(RoomEventType.SETTINGS_CHANGED, command.actorId());
        });
    }

    // 게임 시작. 방장만 호출할 수 있고 두 명이 모여 있어야 함
    // 기존에는 인증도 소속 확인도 없어 아무나 진행 중인 판을 초기화할 수 있었다
    public RoomEvent start(RoomCommand.StartGame command) {
        return inRoom(command.roomId(), room -> {
            requireHost(room, command.actorId());
            if (!room.isFull()) {
                throw new CustomException(ErrorCode.NOT_ENOUGH_PLAYERS);
            }
            if (room.isPlaying()) {
                throw new CustomException(ErrorCode.GAME_ALREADY_STARTED);
            }

            MorrisGame game = room.start();
            log.info(
                    "게임 시작 roomId={} black={} white={} 선공규칙={}",
                    room.roomId(),
                    room.blackId(),
                    room.whiteId(),
                    room.firstMoveRule());
            return RoomEvent.of(RoomEventType.STARTED, GameStateResponse.of(room, game));
        });
    }

    // 착수. 방 단위 락 안에서 규칙 엔진에 위임한다
    public PlayOutcome play(RoomCommand.PlayMove command) {
        return inRoom(command.roomId(), room -> applyMove(room, command.actorId(), command.move()));
    }

    private PlayOutcome applyMove(Room room, long actorId, Move move) {
        if (!room.isPlaying()) {
            return reject(RejectReason.GAME_NOT_IN_PROGRESS);
        }
        Stone actor = room.stoneOf(actorId);
        if (actor == null) {
            // 방에 속하지 않은 사람이 보낸 프레임
            return reject(RejectReason.NOT_YOUR_TURN);
        }

        MorrisGame game = room.game();
        MoveResult result = game.apply(actor, move);

        return switch (result) {
            case MoveResult.Rejected rejected -> {
                // 반복되면 프론트와 서버의 규칙 구현이 어긋났거나 조작 시도다
                log.warn("규칙 위반 거절 roomId={} userId={} 사유={} 수={}", room.roomId(), actorId, rejected.reason(), move);
                yield reject(rejected.reason());
            }
            case MoveResult.Finished finished -> {
                settle(room, game, finished.outcome());
                yield broadcast(RoomEventType.FINISHED, room, game);
            }
            case MoveResult.MillFormed ignored -> broadcast(RoomEventType.STATE_CHANGED, room, game);
            case MoveResult.Applied ignored -> broadcast(RoomEventType.STATE_CHANGED, room, game);
        };
    }

    // 소켓이 끊긴 사용자를 기권 처리
    // 기존에는 방만 지우고 승패도 점수도 남기지 않아 질 것 같으면 창을 닫는 게 이득이었다
    public Optional<RoomBroadcast> handleDisconnect(long userId) {
        return rooms.findByPlayer(userId)
                .flatMap(found -> rooms.mutate(found.roomId(), room -> {
                    long roomId = room.roomId();
                    if (!room.isPlaying()) {
                        if (room.hostId() == userId) {
                            rooms.remove(room.roomId());
                        } else {
                            room.leaveGuest();
                        }
                        return new RoomBroadcast(roomId, RoomEvent.by(RoomEventType.PLAYER_LEFT, userId));
                    }

                    MorrisGame game = room.game();
                    MoveResult result = game.apply(room.stoneOf(userId), new Move.Resign());
                    if (result instanceof MoveResult.Finished finished) {
                        settle(room, game, finished.outcome());
                        log.warn("게임 중 연결 끊김으로 기권 처리 roomId={} userId={}", room.roomId(), userId);
                    }
                    return new RoomBroadcast(
                            roomId,
                            RoomEvent.of(RoomEventType.OPPONENT_DISCONNECTED, GameStateResponse.of(room, game)));
                }));
    }

    // 재접속 복구. 기존에는 SYNC_GAME 이 선언만 되어 있고 새로고침하면 판을 잃었다
    public Optional<RoomEvent> snapshot(long roomId) {
        return rooms.find(roomId)
                .filter(Room::isPlaying)
                .map(room -> RoomEvent.of(RoomEventType.SNAPSHOT, GameStateResponse.of(room, room.game())));
    }

    private void settle(Room room, MorrisGame game, Outcome outcome) {
        room.finish();
        Long winnerId = outcome.isDraw() ? null : room.userIdOf(outcome.winner());
        matchResultService.record(
                room.blackId(), room.whiteId(), winnerId, outcome.reason().name(), game.totalMoves());
    }

    private PlayOutcome broadcast(RoomEventType type, Room room, MorrisGame game) {
        return new PlayOutcome.Broadcast(RoomEvent.of(type, GameStateResponse.of(room, game)));
    }

    private PlayOutcome reject(RejectReason reason) {
        return new PlayOutcome.Reject(MoveRejectedResponse.of(reason));
    }

    private <R> R inRoom(long roomId, java.util.function.Function<Room, R> action) {
        return rooms.mutate(roomId, action).orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private void requireHost(Room room, long actorId) {
        if (room.hostId() != actorId) {
            throw new CustomException(ErrorCode.NOT_ROOM_HOST);
        }
    }
}
