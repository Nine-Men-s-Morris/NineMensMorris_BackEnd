package com.ninemensmorris.common.response;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SYSTEM_EXCEPTION(500,  "Internal Server Error"),
    NOT_FOUND_HANDLER(404, "404 NOT FOUND"),

    // Game Room
    GAME_ROOM_NOT_FOUND(404, "게임 방을 찾을 수 없습니다."),
    FAILED_TO_JOIN_GAME(400, "게임 참가 요청이 실패하였습니다."),
    FAILED_TO_LEAVE_GAME_ROOM(400, "게임 나가기 요청이 실패하였습니다.");

    private final int status;
    private final String message;

    ErrorCode(final int status, final String message) {
        this.status = status;
        this.message = message;
    }
}
