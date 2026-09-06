package com.ninemensmorris.game.dto.response;

import com.ninemensmorris.game.domain.FirstMoveRule;
import com.ninemensmorris.game.domain.Room;

public record RoomSummaryResponse(
        long roomId,
        String title,
        long hostId,
        String hostNickname,
        String hostImageUrl,
        int hostRating,
        int playerCount,
        boolean playing,
        FirstMoveRule firstMoveRule) {

    // 방장 정보는 방에 스냅샷으로 복사하지 않고 조회 시점에 채운다
    // 기존 GameRoom 은 hostScore 를 방 생성 시점 값으로 박아둬서 점수가 올라도 목록이 옛값이었다
    public static RoomSummaryResponse of(Room room, String nickname, String imageUrl, int rating) {
        return new RoomSummaryResponse(
                room.roomId(),
                room.title(),
                room.hostId(),
                nickname,
                imageUrl,
                rating,
                room.playerCount(),
                room.isPlaying(),
                room.firstMoveRule());
    }
}
