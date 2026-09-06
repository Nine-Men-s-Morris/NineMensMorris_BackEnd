package com.ninemensmorris.game.controller;

import com.ninemensmorris.game.command.RoomCommand;
import com.ninemensmorris.game.dto.request.CreateRoomRequest;
import com.ninemensmorris.game.dto.response.CreateRoomResponse;
import com.ninemensmorris.game.dto.response.RoomSummaryResponse;
import com.ninemensmorris.game.service.RoomService;
import com.ninemensmorris.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public List<RoomSummaryResponse> findAll() {
        return roomService.findAll();
    }

    @PostMapping
    public ResponseEntity<CreateRoomResponse> create(
            AuthenticatedUser actor, @Valid @RequestBody CreateRoomRequest request) {
        CreateRoomResponse response = roomService.create(new RoomCommand.CreateRoom(actor.id(), request.title()));
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + response.roomId()))
                .body(response);
    }

    // 입장은 "방에 플레이어를 추가"하는 것이므로 하위 리소스 생성이다
    // 기존에는 userId 를 본문으로 받아 남의 계정으로 입장시킬 수 있었다
    @PostMapping("/{roomId}/players")
    public ResponseEntity<Void> join(AuthenticatedUser actor, @PathVariable long roomId) {
        roomService.join(new RoomCommand.JoinRoom(actor.id(), roomId));
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/{roomId}/players/me")
    public ResponseEntity<Void> leave(AuthenticatedUser actor, @PathVariable long roomId) {
        roomService.leave(new RoomCommand.LeaveRoom(actor.id(), roomId));
        return ResponseEntity.noContent().build();
    }
}
