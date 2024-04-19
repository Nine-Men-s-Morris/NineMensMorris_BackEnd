package com.ninemensmorris.common.response;

import lombok.Getter;

@Getter
public enum MorrisResponseCode {

    GAME_START("게임을 시작합니다."),
    THREE_IN_A_ROW("3개 연속입니다. 돌을 제거하세요."),
    STONE_PLACEMENT_SUCCESS("돌을 성공적으로 놓았습니다."),
    TURN_CHANGE("턴이 변경되었습니다."),
    CANNOT_REMOVE("3개 연속입니다. 돌을 제거하세요."),
    CANNOT_REMOVE_ROW_COLUMN("3행 또는 3열에 속한 돌은 제거할 수 없습니다."),
    STONE_REMOVAL_SUCCESS("돌을 성공적으로 제거하였습니다."),
    GAME_OVER("게임이 종료되었습니다."),
    GAME_TIE("게임이 무승부 처리되었습니다."),
    NO_WINNER("승리자가 없습니다. (게임 승패 판단 로직 에러)");

    private String message;

    MorrisResponseCode(String message) {
        this.message = message;
    }
}
