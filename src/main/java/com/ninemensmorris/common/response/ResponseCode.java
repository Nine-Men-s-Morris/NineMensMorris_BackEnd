package com.ninemensmorris.common.response;

import lombok.Getter;

@Getter
public enum ResponseCode {

    /* Common */
    SUCCESS("Success"),
    ERROR("Error"),

    /* Authentication */
    UNAUTHORIZED("Unauthorized"),
    FORBIDDEN("Forbidden"),
    NOT_FOUND("Not Found"),
    METHOD_NOT_ALLOWED("Method Not Allowed"),

    /* TOKEN */
    OAUTH2_LOGIN("Kakao 로그인이 완료되었습니다."),
    TOKEN_LOGOUT("로그아웃을 완료하였습니다.");



    private final String message;

    ResponseCode(String message) {
        this.message = message;
    }
}
