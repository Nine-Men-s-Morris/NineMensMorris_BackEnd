package com.ninemensmorris.core.move;

// 규칙 위반으로 거절된 이유
// 기존 구현에는 거절이라는 개념 자체가 없어 잘못된 입력이 그대로 판을 오염시켰음
public enum RejectReason {
    GAME_NOT_IN_PROGRESS, // 이미 끝난 게임
    NOT_YOUR_TURN, // 상대 차례
    OUT_OF_BOARD, // 지점 번호가 0..23 밖
    WRONG_PHASE, // 현재 단계에서 할 수 없는 종류의 수
    REMOVAL_PENDING, // 제거할 차례인데 다른 수를 둠
    REMOVAL_NOT_PENDING, // 제거할 차례가 아닌데 제거를 시도
    OCCUPIED, // 목적지에 이미 돌이 있음
    EMPTY_SOURCE, // 출발지가 빈 지점
    NOT_YOUR_STONE, // 상대 돌을 옮기려 함
    NOT_OPPONENT_STONE, // 상대 돌이 아닌 것을 제거하려 함
    NOT_ADJACENT, // MOVING 인데 인접하지 않은 곳으로 이동
    PROTECTED_BY_MILL, // 밀에 속한 돌. 단 상대 돌이 전부 밀이면 제거 가능
    NO_DRAW_OFFER, // 무승부 제안이 없는데 응답
    CANNOT_ACCEPT_OWN_OFFER // 자기 제안을 자기가 수락
}
