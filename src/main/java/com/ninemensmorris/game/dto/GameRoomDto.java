package com.ninemensmorris.game.dto;

import com.ninemensmorris.game.domain.GameRoom;
import lombok.Getter;

@Getter
public class GameRoomDto {

    private final Long roomId;
    private final String roomTitle;
    private final String host;

    public GameRoomDto(GameRoom gameRoom) {
        this.roomId = gameRoom.getId();
        this.roomTitle = gameRoom.getRoomTitle();
        this.host = gameRoom.getHost();
    }
}
