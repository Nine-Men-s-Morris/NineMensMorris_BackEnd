package com.ninemensmorris.core.move;

import com.ninemensmorris.core.game.Outcome;

// apply 의 결과. Rejected 면 판은 바뀌지 않음
public sealed interface MoveResult {

    // 정상 처리되고 턴이 넘어감
    record Applied() implements MoveResult {}

    // 밀이 만들어져 제거할 차례가 됨. 턴은 넘어가지 않음
    record MillFormed() implements MoveResult {}

    // 게임이 끝남
    record Finished(Outcome outcome) implements MoveResult {}

    // 규칙 위반으로 거절됨
    record Rejected(RejectReason reason) implements MoveResult {}
}
