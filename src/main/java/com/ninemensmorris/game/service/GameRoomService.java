package com.ninemensmorris.game.service;

import com.ninemensmorris.game.domain.GameRoom;
import com.ninemensmorris.game.dto.GameRoomRequestDto;
import com.ninemensmorris.game.dto.GameRoomResponseDto;
import com.ninemensmorris.game.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;

    public GameRoomResponseDto createGame(GameRoomRequestDto requestDto) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = authentication.getName();

        GameRoom gameRoom = new GameRoom();
        gameRoom.setRoomTitle(requestDto.getRoomTitle());
        gameRoom.setHost(currentUser);

        gameRoomRepository.save(gameRoom);

        GameRoomResponseDto responseDto = new GameRoomResponseDto();
        responseDto.setRoomTitle(gameRoom.getRoomTitle());
        responseDto.setHost(gameRoom.getHost());
        return responseDto;
    }

    public Optional<GameRoom> getGameById(Long roomId) {
        return gameRoomRepository.findById(roomId);
    }

    public void removeGame(Long roomId) {
        gameRoomRepository.deleteById(roomId);
    }
}