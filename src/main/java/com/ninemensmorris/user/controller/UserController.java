package com.ninemensmorris.user.controller;

import com.ninemensmorris.game.dto.GameRoomDto;
import com.ninemensmorris.user.dto.UserRankDto;
import com.ninemensmorris.user.dto.UserResponseDto;
import com.ninemensmorris.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/user")
    public ResponseEntity<UserResponseDto> getUser() {
        UserResponseDto userResponseDto = userService.getUser();
        return ResponseEntity.ok(userResponseDto);
    }

    @GetMapping("/api/rank")
    public List<UserRankDto> getUserRanks() {
        return userService.getUserRanks();
    }
}
