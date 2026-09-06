package com.ninemensmorris.game.dto.response;

// 방 토픽 하나로 흐르는 이벤트 종류
// 기존에는 /topic/game 과 /topic/gameRoom 두 곳으로 나뉘어 있었고
// 후자는 아무도 구독하지 않아 메시지가 어디에도 도달하지 않았다
public enum RoomEventType {
    PLAYER_JOINED,
    PLAYER_LEFT,
    SETTINGS_CHANGED, // 선공 방식 변경
    STARTED,
    STATE_CHANGED, // 착수 결과
    FINISHED, // 승패 또는 무승부 확정
    DRAW_OFFERED,
    DRAW_DECLINED,
    SNAPSHOT, // 재접속 시 현재 판 전체
    OPPONENT_DISCONNECTED
}
