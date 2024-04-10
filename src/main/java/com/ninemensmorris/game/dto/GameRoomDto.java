package com.ninemensmorris.game.dto;

import com.ninemensmorris.game.domain.GameRoom;
import lombok.Getter;

@Getter
public class GameRoomDto {

    private final Long roomId;
    private final String roomTitle;
    private final String host;
    private final String hostImageUrl;
    private final int playerCount;
    private final int hostScore;

    public GameRoomDto(GameRoom gameRoom, int playerCount) {
        this.roomId = gameRoom.getId();
        this.roomTitle = gameRoom.getRoomTitle();
        this.host = gameRoom.getHost();
        this.hostScore = gameRoom.getHostScore();
        this.hostImageUrl = gameRoom.getHostImageUrl();
        this.playerCount = playerCount;
    }
}
