package com.ninemensmorris.game.controller;

import com.ninemensmorris.game.dto.GameRoomRequestDto;
import com.ninemensmorris.game.dto.GameRoomResponseDto;
import com.ninemensmorris.game.service.GameRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;

    @PostMapping("/api/createGame")
    public ResponseEntity<GameRoomResponseDto> createGame(@RequestBody GameRoomRequestDto requestDto) {
        GameRoomResponseDto responseDto = gameRoomService.createGame(requestDto);
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }
}
