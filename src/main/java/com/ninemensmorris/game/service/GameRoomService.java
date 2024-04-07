package com.ninemensmorris.game.service;

import com.ninemensmorris.game.domain.GameRoom;
import com.ninemensmorris.game.dto.CreateGameRequestDto;
import com.ninemensmorris.game.dto.CreateGameResponseDto;
import com.ninemensmorris.game.dto.GameRoomDto;
import com.ninemensmorris.game.repository.GameRoomRepository;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public List<GameRoomDto> getAllGameRooms() {
        List<GameRoom> gameRooms = gameRoomRepository.findAll();
        return gameRooms.stream()
                .map(GameRoomDto::new)
                .collect(Collectors.toList());
    }

    public CreateGameResponseDto createGame(CreateGameRequestDto requestDto) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserId = authentication.getName();

        User currentUser = userRepository.findByUserId(Long.parseLong(currentUserId));
        String currentNickname = currentUser.getNickname();

        GameRoom gameRoom = new GameRoom();
        gameRoom.setRoomTitle(requestDto.getRoomTitle());
        gameRoom.setHost(currentNickname);

        gameRoomRepository.save(gameRoom);

        CreateGameResponseDto responseDto = new CreateGameResponseDto();
        responseDto.setRoomTitle(gameRoom.getRoomTitle());
        responseDto.setHost(gameRoom.getHost());
        return responseDto;
    }

    public void removeGame(Long roomId) {
        gameRoomRepository.deleteById(roomId);
    }
}