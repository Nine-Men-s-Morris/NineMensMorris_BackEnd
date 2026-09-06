package com.ninemensmorris.game.domain;

// 선공을 정하는 방식. 방을 다시 만들지 않고 시작 직전까지 바꿀 수 있음
public enum FirstMoveRule {
    RANDOM, // 서버가 시작 시점에 난수로 결정
    HOST_FIRST, // 방장 선공
    GUEST_FIRST // 참가자 선공
}
