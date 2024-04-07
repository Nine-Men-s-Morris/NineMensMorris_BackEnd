package com.ninemensmorris.game.controller;

import com.ninemensmorris.game.dto.CreateGameRequestDto;
import com.ninemensmorris.game.dto.CreateGameResponseDto;
import com.ninemensmorris.game.dto.GameRoomDto;
import com.ninemensmorris.game.service.GameRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;

    @GetMapping("/api/games")
    public ResponseEntity<List<GameRoomDto>> getAllGameRooms() {
        List<GameRoomDto> gameRooms = gameRoomService.getAllGameRooms();
        return ResponseEntity.ok(gameRooms);
    }

    @PostMapping("/api/createGame")
    public ResponseEntity<CreateGameResponseDto> createGame(@RequestBody CreateGameRequestDto requestDto) {
        CreateGameResponseDto responseDto = gameRoomService.createGame(requestDto);
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }
}
