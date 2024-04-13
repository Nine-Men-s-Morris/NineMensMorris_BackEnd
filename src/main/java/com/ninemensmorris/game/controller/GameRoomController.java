package com.ninemensmorris.game.controller;

import com.ninemensmorris.game.dto.CreateGameRequestDto;
import com.ninemensmorris.game.dto.CreateGameResponseDto;
import com.ninemensmorris.game.dto.GameRoomDto;
import com.ninemensmorris.game.service.GameRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/api/joinGame/{roomId}")
    public ResponseEntity<String> joinGame(@PathVariable Long roomId) {
        boolean success = gameRoomService.joinGame(roomId);
        if (success) {
            return new ResponseEntity<>("Successfully joined the game.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Failed to join the game.", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/api/leaveGame/{roomId}")
    public ResponseEntity<String> leaveGame(@PathVariable Long roomId) {
        boolean success = gameRoomService.leaveGame(roomId);
        if (success) {
            return new ResponseEntity<>("Successfully left the game room.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Failed to leave the game room.", HttpStatus.BAD_REQUEST);
        }
    }
}
