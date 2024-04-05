package com.ninemensmorris.game.repository;

import com.ninemensmorris.game.domain.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
}
