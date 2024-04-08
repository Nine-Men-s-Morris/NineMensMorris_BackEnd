package com.ninemensmorris.game.controller;

import com.ninemensmorris.game.dto.RemoveOpponentStoneRequestDto;
import com.ninemensmorris.game.dto.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.StonePlacementResponseDto;
import com.ninemensmorris.game.service.GameRoomService;
import com.ninemensmorris.game.service.MorrisService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MorrisController {

    private final GameRoomService gameRoomService;
    private final MorrisService morrisService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/joinGame")
    public void joinGame(@Payload Long roomId) {
        // 클라이언트에게 게임 방 참가 메시지를 전송
        simpMessagingTemplate.convertAndSend("/topic/gameRoom", "게임 방에 참가했습니다.");

        boolean success = gameRoomService.joinGame(roomId);
    }

    @MessageMapping("/game/startGame")
    public void startNewGame(@Payload Long gameId) {
        morrisService.startGame(gameId);
        simpMessagingTemplate.convertAndSend("/topic/game", "게임을 시작합니다.");
    }

    @MessageMapping("/game/placeStone")
    public void placeStone(@Payload StonePlacementRequestDto placementRequest) {
        StonePlacementResponseDto placementResponse = morrisService.placeStone(placementRequest);
        simpMessagingTemplate.convertAndSend("/topic/game", placementResponse);
    }

    @MessageMapping("/game/removeOpponentStone")
    public void removeOpponentStone(@Payload RemoveOpponentStoneRequestDto requestDto) {
        StonePlacementResponseDto removalResponse = morrisService.removeOpponentStone(requestDto);
        simpMessagingTemplate.convertAndSend("/topic/game", removalResponse);
    }
}
