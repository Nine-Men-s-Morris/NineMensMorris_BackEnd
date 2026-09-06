package com.ninemensmorris.core.game;

public enum EndReason {
    STONES_EXHAUSTED, // 돌이 2개 이하로 줄어듦
    NO_LEGAL_MOVE, // 둘 수 있는 수가 없음
    RESIGN, // 기권
    DRAW_AGREED, // 합의 무승부
    THREEFOLD_REPETITION, // 같은 국면이 3번 반복
    FIFTY_MOVE_RULE // 제거 없이 50수 경과
}
