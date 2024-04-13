package com.ninemensmorris.game.controller;

import com.ninemensmorris.game.dto.MorrisResultDto;
import com.ninemensmorris.game.dto.RemoveOpponentStoneRequestDto;
import com.ninemensmorris.game.dto.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.StonePlacementResponseDto;
import com.ninemensmorris.game.service.GameRoomService;
import com.ninemensmorris.game.service.MorrisService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
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

    @MessageMapping("/joinGame/{roomId}")
    public void joinGame(@DestinationVariable Long roomId) {
        gameRoomService.joinGame(roomId);
        simpMessagingTemplate.convertAndSend("/topic/gameRoom/" + roomId, roomId + "번 게임 방에 참가했습니다.");
    }

    @MessageMapping("/game/startGame")
    public void startNewGame(@Payload Long gameId) {
        morrisService.startGame(gameId);
        simpMessagingTemplate.convertAndSend("/topic/game/" + gameId, "게임을 시작합니다.");
    }

    @MessageMapping("/game/placeStone")
    public void placeStone(@Payload StonePlacementRequestDto requestDto) {
        StonePlacementResponseDto placementResponse = morrisService.placeStone(requestDto);
        simpMessagingTemplate.convertAndSend("/topic/game/" + requestDto.getGameId(), placementResponse);
    }

    @MessageMapping("/game/removeOpponentStone")
    public void removeOpponentStone(@Payload RemoveOpponentStoneRequestDto requestDto) {
        StonePlacementResponseDto removalResponse = morrisService.removeOpponentStone(requestDto);
        simpMessagingTemplate.convertAndSend("/topic/game/" + requestDto.getGameId(), removalResponse);
    }

    @MessageMapping("/game/handleMorrisResult")
    public void handleMorrisResult(@Payload MorrisResultDto morrisResult) {
        StonePlacementResponseDto response = morrisService.handleMorrisResult(morrisResult);
        simpMessagingTemplate.convertAndSend("/topic/game/" + morrisResult.getGameId(), response);
    }
}
