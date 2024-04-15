package com.ninemensmorris.common.response;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MorrisResponse<T> {

    private ResponseType type;
    private T data;

    public static <T> MorrisResponse<T> response(ResponseType type, T data) {
        return MorrisResponse.<T>builder()
                .type(type)
                .data(data)
                .build();
    }

    public enum ResponseType {
        SYNC_GAME, ERROR, GAME_START, GAME_STATE_UPDATE, GAME_OVER
    }
}
