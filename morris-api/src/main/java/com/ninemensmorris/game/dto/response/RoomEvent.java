package com.ninemensmorris.game.dto.response;

// 방 토픽으로 나가는 메시지. state 는 판이 있을 때만 채워짐
public record RoomEvent(RoomEventType type, GameStateResponse state, Long actorId, String message) {

    public static RoomEvent of(RoomEventType type, GameStateResponse state) {
        return new RoomEvent(type, state, null, null);
    }

    public static RoomEvent by(RoomEventType type, long actorId) {
        return new RoomEvent(type, null, actorId, null);
    }
}
