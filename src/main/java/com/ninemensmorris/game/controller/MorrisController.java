package com.ninemensmorris.game.controller;

import com.ninemensmorris.common.response.MorrisResponse;
import com.ninemensmorris.game.dto.Morris.RemoveOpponentStoneRequestDto;
import com.ninemensmorris.game.dto.Morris.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.Morris.StonePlacementResponseDto;
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
    public void joinGame(@DestinationVariable Long roomId, Long userId) {
        boolean gameStarted = gameRoomService.joinGame(roomId, userId);
        if (gameStarted) {
            startGame(roomId);
        }
        simpMessagingTemplate.convertAndSend("/topic/gameRoom/" + roomId, roomId + "번 게임 방에 참가했습니다.");
    }

    @MessageMapping("/game/startGame")
    public void startGame(Long gameId) {
        MorrisResponse<StonePlacementResponseDto> gameStartResponse = morrisService.startGame(gameId);
        simpMessagingTemplate.convertAndSend("/topic/game/" + gameId, gameStartResponse);
    }

    @MessageMapping("/game/placeStone")
    public void placeStone(StonePlacementRequestDto requestDto) {
        StonePlacementResponseDto placementResponse = morrisService.placeStone(requestDto);
        simpMessagingTemplate.convertAndSend("/topic/game/" + requestDto.getGameId(), placementResponse);
    }

    @MessageMapping("/game/removeOpponentStone")
    public void removeOpponentStone(RemoveOpponentStoneRequestDto requestDto) {
        StonePlacementResponseDto removalResponse = morrisService.removeOpponentStone(requestDto);
        simpMessagingTemplate.convertAndSend("/topic/game/" + requestDto.getGameId(), removalResponse);
    }

//    @MessageMapping("/game/handleMorrisResult")
//    public void handleMorrisResult(@Payload Long gameId) {
//        StonePlacementResponseDto response = morrisService.handleMorrisResult(gameId);
//        simpMessagingTemplate.convertAndSend("/topic/game/" + gameId, response);
//    }
}
