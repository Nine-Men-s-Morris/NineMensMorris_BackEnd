package com.ninemensmorris.user.controller;

import com.ninemensmorris.user.dto.UserNicknameResponseDto;
import com.ninemensmorris.user.dto.UserRankDto;
import com.ninemensmorris.user.dto.UserResponseDto;
import com.ninemensmorris.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<List<UserRankDto>> getUserRanks() {
        List<UserRankDto> userRanks = userService.getUserRanks();
        return ResponseEntity.ok(userRanks);
    }

    @GetMapping("/api/user/{userId}")
    public ResponseEntity<UserNicknameResponseDto> getUserNickname(@PathVariable Long userId) {
        UserNicknameResponseDto responseDto = userService.getUserNickname(userId);
        return ResponseEntity.ok(responseDto);
    }
}
