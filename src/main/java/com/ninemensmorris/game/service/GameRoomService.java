package com.ninemensmorris.game.service;

import com.ninemensmorris.common.exception.CustomException;
import com.ninemensmorris.common.response.ErrorCode;
import com.ninemensmorris.game.domain.GameRoom;
import com.ninemensmorris.game.dto.GameRoom.CreateGameRequestDto;
import com.ninemensmorris.game.dto.GameRoom.CreateGameResponseDto;
import com.ninemensmorris.game.dto.GameRoom.GameRoomDto;
import com.ninemensmorris.game.repository.GameRoomRepository;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
        if (gameRoom.getPlayerTwoId() != null) {
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

    @Transactional
    public CreateGameResponseDto createGame(CreateGameRequestDto requestDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserId = authentication.getName();
        String currentNickname = currentUserNickname();
        User hostUser = userRepository.findByNickname(currentNickname);

        GameRoom gameRoom = new GameRoom();
        gameRoom.setRoomTitle(requestDto.getRoomTitle());
        gameRoom.setHost(currentNickname);
        gameRoom.setHostImageUrl(hostUser.getImageUrl());
        gameRoom.setHostScore(hostUser.getScore());
        gameRoom.setPlayerOneId(Long.parseLong(currentUserId));

        GameRoom savedGameRoom = gameRoomRepository.save(gameRoom);

        return CreateGameResponseDto.builder()
                .roomId(savedGameRoom.getId())
                .roomTitle(savedGameRoom.getRoomTitle())
                .host(savedGameRoom.getHost())
                .build();
    }

    @Transactional
    public boolean joinGame(Long roomId, Long userId) {
        Optional<GameRoom> optionalGameRoom = gameRoomRepository.findById(roomId);
        if (optionalGameRoom.isPresent()) {
            GameRoom gameRoom = optionalGameRoom.get();
            if (gameRoom.getPlayerTwoId() == null) {
                gameRoom.setPlayerTwoId(userId);
            }
            gameRoomRepository.save(gameRoom);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean leaveGame(Long roomId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = Long.parseLong(authentication.getName());
        Optional<GameRoom> optionalGameRoom = gameRoomRepository.findById(roomId);
        if (optionalGameRoom.isPresent()) {
            GameRoom gameRoom = optionalGameRoom.get();
            if (currentUserId.equals(gameRoom.getPlayerOneId())) {
                gameRoomRepository.delete(gameRoom);
                return true;
            } else if (currentUserId.equals(gameRoom.getPlayerTwoId())) {
                gameRoom.setPlayerTwoId(null);
                gameRoomRepository.save(gameRoom);
                return true;
            } else {
                return false;
            }
        } else {
            throw new CustomException(ErrorCode.GAME_ROOM_NOT_FOUND);
        }
    }
}