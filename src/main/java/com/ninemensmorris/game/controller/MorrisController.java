package com.ninemensmorris.game.controller;

import com.ninemensmorris.game.dto.StonePlacementRequestDto;
import com.ninemensmorris.game.dto.StonePlacementResponseDto;
import com.ninemensmorris.game.service.MorrisService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MorrisController {

    private final MorrisService morrisService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/game/placeStone")
    public void placeStone(@Payload StonePlacementRequestDto placementRequest) {

        StonePlacementResponseDto placementResponse = morrisService.placeStone(placementRequest);
        simpMessagingTemplate.convertAndSend("/topic/game", placementResponse);
    }
}
