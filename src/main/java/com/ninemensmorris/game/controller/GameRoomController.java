package com.ninemensmorris.game.controller;

import com.ninemensmorris.game.service.GameRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameService;

    @MessageMapping("/createGameRoom")
    @SendTo("/morris/gameRoomCreated")
    public void createGameRoom(Long roomId) {
        gameService.createGameRoom(roomId);
    }

    @MessageMapping("/joinGameRoom")
    @SendTo("/morris/gameRoomJoined")
    public void joinWaitRoom(Long roomId, Long userId) {
        gameService.addUserToGameRoom(roomId, userId);
    }

    @MessageMapping("/leaveGameRoom")
    @SendTo("/morris/gameRoomLeft")
    public void leaveWaitRoom(Long roomId, Long userId) {
        gameService.removeUserFromGameRoom(roomId, userId);
    }

}
