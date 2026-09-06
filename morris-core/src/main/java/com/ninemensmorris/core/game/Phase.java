package com.ninemensmorris.core.game;

public enum Phase {
    PLACING, // 손에 든 돌을 빈 지점에 놓음
    MOVING, // 인접한 빈 지점으로만 이동
    FLYING // 돌이 3개만 남아 인접 제약 없이 이동
}
