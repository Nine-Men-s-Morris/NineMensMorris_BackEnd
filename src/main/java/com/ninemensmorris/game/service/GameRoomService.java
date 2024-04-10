package com.ninemensmorris.game.service;

import com.ninemensmorris.game.domain.GameRoom;
import com.ninemensmorris.game.dto.CreateGameRequestDto;
import com.ninemensmorris.game.dto.CreateGameResponseDto;
import com.ninemensmorris.game.dto.GameRoomDto;
import com.ninemensmorris.game.repository.GameRoomRepository;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;
    private final UserRepository userRepository;

    private String currentUserNickname() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserId = authentication.getName();

        User currentUser = userRepository.findByUserId(Long.parseLong(currentUserId));

        return currentUser.getNickname();
    }

    private int calculatePlayerCount(GameRoom gameRoom) {
        int playerCount = 1;
        if (gameRoom.getPlayerTwoNickname() != null) {
            playerCount++;
        }
        return playerCount;
    }

    public List<GameRoomDto> getAllGameRooms() {
        List<GameRoom> gameRooms = gameRoomRepository.findAll();
        return gameRooms.stream()
                .map(gameRoom -> {
                    int playerCount = calculatePlayerCount(gameRoom);
                    return new GameRoomDto(gameRoom, playerCount);
                })
                .collect(Collectors.toList());
    }

    public CreateGameResponseDto createGame(CreateGameRequestDto requestDto) {

        String currentNickname = currentUserNickname();
        User hostUser = userRepository.findByNickname(currentNickname);

        GameRoom gameRoom = new GameRoom();
        gameRoom.setRoomTitle(requestDto.getRoomTitle());
        gameRoom.setHost(currentNickname);
        gameRoom.setHostImageUrl(hostUser.getImageUrl());
        gameRoom.setHostScore(hostUser.getScore());
        gameRoom.setPlayerOneNickname(currentNickname);

        GameRoom savedGameRoom = gameRoomRepository.save(gameRoom);

        return CreateGameResponseDto.builder()
                .roomId(savedGameRoom.getId())
                .roomTitle(savedGameRoom.getRoomTitle())
                .host(savedGameRoom.getHost())
                .build();
    }

    public boolean joinGame(Long roomId) {

        String currentNickname = currentUserNickname();

        Optional<GameRoom> optionalGameRoom = gameRoomRepository.findById(roomId);
        if (optionalGameRoom.isPresent()) {
            GameRoom gameRoom = optionalGameRoom.get();
            if (gameRoom.getPlayerTwoNickname() == null) {
                gameRoom.setPlayerTwoNickname(currentNickname);
            }
            gameRoomRepository.save(gameRoom);
            return true;
        }
        return false;
    }

    public void removeGame(Long roomId) {
        gameRoomRepository.deleteById(roomId);
    }
}