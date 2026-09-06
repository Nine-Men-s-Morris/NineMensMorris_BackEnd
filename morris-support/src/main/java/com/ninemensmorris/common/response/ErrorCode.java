package com.ninemensmorris.common.response;

import lombok.Getter;

@Getter
public enum ErrorCode {
    NOT_FOUND_USER(404, "해당 사용자를 찾을 수 없습니다."),
    UNAUTHORIZED(401, "로그인이 필요합니다."),

    // Room
    ROOM_NOT_FOUND(404, "방을 찾을 수 없습니다."),
    ROOM_FULL(400, "방이 가득 찼습니다."),
    ALREADY_JOINED(400, "이미 참가한 방입니다."),
    NOT_ROOM_HOST(403, "방장만 할 수 있습니다."),
    NOT_ENOUGH_PLAYERS(400, "두 명이 모여야 시작할 수 있습니다."),
    GAME_ALREADY_STARTED(400, "이미 시작된 게임입니다.");

    private final int status;
    private final String message;

    ErrorCode(final int status, final String message) {
        this.status = status;
        this.message = message;
    }
}
