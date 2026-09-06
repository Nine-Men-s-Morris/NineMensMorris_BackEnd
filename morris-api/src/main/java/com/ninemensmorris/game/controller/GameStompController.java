package com.ninemensmorris.game.controller;

import com.ninemensmorris.core.move.Move;
import com.ninemensmorris.game.command.RoomCommand;
import com.ninemensmorris.game.dto.request.FirstMoveRuleRequest;
import com.ninemensmorris.game.dto.request.MoveRequest;
import com.ninemensmorris.game.dto.request.PlaceRequest;
import com.ninemensmorris.game.dto.request.RemoveRequest;
import com.ninemensmorris.game.dto.response.RoomEvent;
import com.ninemensmorris.game.service.GameService;
import com.ninemensmorris.game.service.PlayOutcome;
import com.ninemensmorris.security.AuthenticatedUser;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

// 게임 진행 STOMP 목적지
//
// roomId 는 항상 경로 변수로 받는다. 페이로드에 식별자를 넣으면 조작 지점이 되고
// 목적지별 인가 검사를 일괄 적용할 수 없다
// 행위자는 Principal 에서만 온다
@Controller
@RequiredArgsConstructor
public class GameStompController {

    private static final String ROOM_TOPIC = "/topic/rooms/";
    private static final String ERROR_QUEUE = "/queue/errors";

    private final GameService gameService;
    private final SimpMessagingTemplate messaging;

    @MessageMapping("/rooms/{roomId}/settings")
    public void changeSettings(
            @DestinationVariable long roomId, @Payload FirstMoveRuleRequest request, Principal principal) {
        AuthenticatedUser actor = require(principal);
        RoomEvent event = gameService.changeFirstMoveRule(
                new RoomCommand.ChangeFirstMoveRule(actor.id(), roomId, request.firstMoveRule()));
        broadcast(roomId, event);
    }

    @MessageMapping("/rooms/{roomId}/start")
    public void start(@DestinationVariable long roomId, Principal principal) {
        AuthenticatedUser actor = require(principal);
        broadcast(roomId, gameService.start(new RoomCommand.StartGame(actor.id(), roomId)));
    }

    // 1단계 배치
    @MessageMapping("/rooms/{roomId}/place")
    public void place(@DestinationVariable long roomId, @Payload PlaceRequest request, Principal principal) {
        play(roomId, principal, new Move.Place(request.to()));
    }

    // 2·3단계 이동. 기존에는 배치와 같은 목적지를 써서 쓰지 않는 필드가 섞여 있었다
    @MessageMapping("/rooms/{roomId}/move")
    public void move(@DestinationVariable long roomId, @Payload MoveRequest request, Principal principal) {
        play(roomId, principal, new Move.Slide(request.from(), request.to()));
    }

    @MessageMapping("/rooms/{roomId}/remove")
    public void remove(@DestinationVariable long roomId, @Payload RemoveRequest request, Principal principal) {
        play(roomId, principal, new Move.Remove(request.at()));
    }

    @MessageMapping("/rooms/{roomId}/resign")
    public void resign(@DestinationVariable long roomId, Principal principal) {
        play(roomId, principal, new Move.Resign());
    }

    @MessageMapping("/rooms/{roomId}/draw-offer")
    public void offerDraw(@DestinationVariable long roomId, Principal principal) {
        play(roomId, principal, new Move.OfferDraw());
    }

    @MessageMapping("/rooms/{roomId}/draw-accept")
    public void acceptDraw(@DestinationVariable long roomId, Principal principal) {
        play(roomId, principal, new Move.RespondDraw(true));
    }

    @MessageMapping("/rooms/{roomId}/draw-decline")
    public void declineDraw(@DestinationVariable long roomId, Principal principal) {
        play(roomId, principal, new Move.RespondDraw(false));
    }

    // 재접속 복구. 요청자에게만 현재 판을 보낸다
    @MessageMapping("/rooms/{roomId}/sync")
    public void sync(@DestinationVariable long roomId, Principal principal) {
        AuthenticatedUser actor = require(principal);
        gameService
                .snapshot(roomId)
                .ifPresent(event -> messaging.convertAndSendToUser(String.valueOf(actor.id()), "/queue/sync", event));
    }

    private void play(long roomId, Principal principal, Move move) {
        AuthenticatedUser actor = require(principal);
        PlayOutcome outcome = gameService.play(new RoomCommand.PlayMove(actor.id(), roomId, move));

        switch (outcome) {
            case PlayOutcome.Broadcast broadcast -> broadcast(roomId, broadcast.event());
            // 거절은 요청자에게만. 방에 뿌리면 상대에게 내 실수가 보인다
            case PlayOutcome.Reject reject ->
                messaging.convertAndSendToUser(String.valueOf(actor.id()), ERROR_QUEUE, reject.response());
        }
    }

    private void broadcast(long roomId, RoomEvent event) {
        messaging.convertAndSend(ROOM_TOPIC + roomId, event);
    }

    private AuthenticatedUser require(Principal principal) {
        AuthenticatedUser actor = AuthenticatedUser.from(principal);
        if (actor == null) {
            throw new IllegalStateException("인증되지 않은 STOMP 연결");
        }
        return actor;
    }
}
