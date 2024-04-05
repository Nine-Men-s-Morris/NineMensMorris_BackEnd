package com.ninemensmorris.game.service;

import com.ninemensmorris.game.domain.GameRoom;
import com.ninemensmorris.game.repository.GameRoomRepository;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;
    private final UserRepository userRepository;

    public GameRoom createGameRoom(Long roomId) {
        GameRoom gameRoom = new GameRoom();
        gameRoom.setRoomId(roomId);
        return gameRoomRepository.save(gameRoom);
    }

    public Optional<GameRoom> getGameRoomById(Long roomId) {
        return gameRoomRepository.findById(roomId);
    }

    public void addUserToGameRoom(Long roomId, Long userId) {
        Optional<GameRoom> optionalGameRoom = gameRoomRepository.findById(roomId);
        if (optionalGameRoom.isPresent()) {
            GameRoom gameRoom = optionalGameRoom.get();
            Optional<User> optionalUser = userRepository.findById(userId);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                gameRoom.addUser(user);
                gameRoomRepository.save(gameRoom);
            }
        }
    }

    public void removeUserFromGameRoom(Long roomId, Long userId) {
        Optional<GameRoom> optionalGameRoom = gameRoomRepository.findById(roomId);
        if (optionalGameRoom.isPresent()) {
            GameRoom gameRoom = optionalGameRoom.get();
            Optional<User> optionalUser = userRepository.findById(userId);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                gameRoom.removeUser(user);
                gameRoomRepository.save(gameRoom);
            }
        }
    }
}