package com.ninemensmorris.core.game;

import com.ninemensmorris.core.board.Stone;

// winner 가 null 이면 무승부
public record Outcome(Stone winner, EndReason reason) {

    public static Outcome win(Stone winner, EndReason reason) {
        return new Outcome(winner, reason);
    }

    public static Outcome draw(EndReason reason) {
        return new Outcome(null, reason);
    }

    public boolean isDraw() {
        return winner == null;
    }
}
