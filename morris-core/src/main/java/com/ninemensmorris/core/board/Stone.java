package com.ninemensmorris.core.board;

// 돌의 색. 선공이 흑이라는 전제는 두지 않고 방 설정에서 정함
public enum Stone {
    BLACK,
    WHITE;

    public Stone opponent() {
        return this == BLACK ? WHITE : BLACK;
    }
}
