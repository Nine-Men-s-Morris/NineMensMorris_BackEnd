package com.ninemensmorris.game.domain;

import com.ninemensmorris.core.board.Stone;
import com.ninemensmorris.core.game.MorrisGame;
import java.time.Instant;
import java.util.Random;

// 방 하나의 상태. 대기 중이면 game 이 null 이고 시작하면 채워짐
//
// 로비와 게임 상태를 DB 가 아니라 메모리에 둔다
// 방의 수명이 수십 초인데 서버가 죽으면 정리되지 않아 고아 행이 쌓였고,
// 진행 중인 판은 어차피 메모리에만 있어 복구할 수도 없었다
public final class Room {

    private static final Random RANDOM = new Random();

    private final long roomId;
    private final String title;
    private final long hostId;
    private final Instant createdAt = Instant.now();

    private Long guestId;
    private FirstMoveRule firstMoveRule = FirstMoveRule.RANDOM;
    private MorrisGame game;

    // 흑/백이 각각 누구인지. 방장이 항상 흑이라는 전제를 두지 않기 위해 분리함
    private long blackId;
    private long whiteId;

    private Instant lastActivityAt = Instant.now();

    public Room(long roomId, String title, long hostId) {
        this.roomId = roomId;
        this.title = title;
        this.hostId = hostId;
    }

    public boolean isFull() {
        return guestId != null;
    }

    public boolean isPlaying() {
        return game != null;
    }

    public boolean contains(long userId) {
        return hostId == userId || (guestId != null && guestId == userId);
    }

    public int playerCount() {
        return isFull() ? 2 : 1;
    }

    public void join(long userId) {
        this.guestId = userId;
        touch();
    }

    public void leaveGuest() {
        this.guestId = null;
        this.game = null;
        touch();
    }

    public void changeFirstMoveRule(FirstMoveRule rule) {
        this.firstMoveRule = rule;
        touch();
    }

    // 선공을 확정하고 게임을 시작. 난수는 반드시 서버가 뽑는다
    public MorrisGame start() {
        boolean hostIsBlack =
                switch (firstMoveRule) {
                    case HOST_FIRST -> true;
                    case GUEST_FIRST -> false;
                    case RANDOM -> RANDOM.nextBoolean();
                };
        this.blackId = hostIsBlack ? hostId : guestId;
        this.whiteId = hostIsBlack ? guestId : hostId;
        this.game = new MorrisGame(Stone.BLACK);
        touch();
        return game;
    }

    public void finish() {
        touch();
    }

    public Stone stoneOf(long userId) {
        if (userId == blackId) {
            return Stone.BLACK;
        }
        if (userId == whiteId) {
            return Stone.WHITE;
        }
        return null;
    }

    public long userIdOf(Stone stone) {
        return stone == Stone.BLACK ? blackId : whiteId;
    }

    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    public long roomId() {
        return roomId;
    }

    public String title() {
        return title;
    }

    public long hostId() {
        return hostId;
    }

    public Long guestId() {
        return guestId;
    }

    public FirstMoveRule firstMoveRule() {
        return firstMoveRule;
    }

    public MorrisGame game() {
        return game;
    }

    public long blackId() {
        return blackId;
    }

    public long whiteId() {
        return whiteId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }
}
