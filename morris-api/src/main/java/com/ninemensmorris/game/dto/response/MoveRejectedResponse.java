package com.ninemensmorris.game.dto.response;

import com.ninemensmorris.core.move.RejectReason;

// 규칙 위반으로 거절됐을 때 요청자에게만 보냄
// 브로드캐스트하면 상대에게 내 실수가 보인다
public record MoveRejectedResponse(String code, String message) {

    public static MoveRejectedResponse of(RejectReason reason) {
        return new MoveRejectedResponse(reason.name(), messageOf(reason));
    }

    // 사용자에게 보이는 문구. "밀" 은 전달되지 않는 용어라 "3연속" 으로 쓴다
    private static String messageOf(RejectReason reason) {
        return switch (reason) {
            case GAME_NOT_IN_PROGRESS -> "이미 끝난 게임입니다.";
            case NOT_YOUR_TURN -> "상대 차례입니다.";
            case OUT_OF_BOARD -> "판 밖의 지점입니다.";
            case WRONG_PHASE -> "지금 단계에서는 할 수 없는 수입니다.";
            case REMOVAL_PENDING -> "먼저 상대 돌을 제거해야 합니다.";
            case REMOVAL_NOT_PENDING -> "지금은 돌을 제거할 수 없습니다.";
            case OCCUPIED -> "이미 돌이 놓인 지점입니다.";
            case EMPTY_SOURCE -> "빈 지점에서는 옮길 수 없습니다.";
            case NOT_YOUR_STONE -> "자기 돌만 옮길 수 있습니다.";
            case NOT_OPPONENT_STONE -> "상대 돌만 제거할 수 있습니다.";
            case NOT_ADJACENT -> "인접한 지점으로만 옮길 수 있습니다.";
            case PROTECTED_BY_MILL -> "3연속에 속한 돌은 제거할 수 없습니다.";
            case NO_DRAW_OFFER -> "무승부 제안이 없습니다.";
            case CANNOT_ACCEPT_OWN_OFFER -> "자기가 한 제안은 수락할 수 없습니다.";
        };
    }
}
