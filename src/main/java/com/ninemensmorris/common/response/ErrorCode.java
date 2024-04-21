package com.ninemensmorris.common.response;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SYSTEM_EXCEPTION(500,  "Internal Server Error"),
    NOT_FOUND_HANDLER(404, "404 NOT FOUND"),
    NOT_FOUND_USER(404, "해당 사용자를 찾을 수 없습니다."),

    // Game Room
    GAME_ROOM_NOT_FOUND(404, "게임 방을 찾을 수 없습니다."),
    GAME_ALREADY_JOIN(403, "이미 게임에 참가한 플레이어입니다."),
    FAILED_TO_JOIN_GAME(400, "게임 참가 요청이 실패하였습니다."),
    FAILED_TO_LEAVE_GAME_ROOM(400, "게임 나가기 요청이 실패하였습니다."),

    // Morris
    NOT_FOUND_SESSION_ID(403, "세션 id 값을 찾을 수 없습니다.");

    private final int status;
    private final String message;

    ErrorCode(final int status, final String message) {
        this.status = status;
        this.message = message;
    }
}
