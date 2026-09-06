package com.ninemensmorris.match.domain;

import com.ninemensmorris.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 대전 기록
//
// users 의 집계값(mmr, 승패무)은 언제든 이 원장으로 재계산할 수 있어야 함
// 승자/패자가 아니라 흑/백으로 저장해야 무승부를 표현할 수 있음
// 변동 전 MMR 을 함께 남겨 K 계수를 바꿔도 과거 경기를 다시 계산할 수 있게 함
@Entity
@Table(
        name = "matches",
        indexes = {
            @Index(name = "idx_matches_black", columnList = "black_id, finished_at"),
            @Index(name = "idx_matches_white", columnList = "white_id, finished_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_id", nullable = false)
    private User black;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_id", nullable = false)
    private User white;

    // null 이면 무승부
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(nullable = false, length = 30)
    private String endReason;

    // 레이팅에 반영된 판인지. 조기 종료나 반복 대전이면 false
    @Column(nullable = false)
    private boolean rated;

    @Column(nullable = false)
    private int blackMmrBefore;

    @Column(nullable = false)
    private int whiteMmrBefore;

    @Column(nullable = false)
    private int blackMmrDelta;

    @Column(nullable = false)
    private int whiteMmrDelta;

    @Column(nullable = false)
    private int moveCount;

    @Column(nullable = false)
    private Instant finishedAt;

    private Match(User black, User white, User winner, String endReason, int moveCount) {
        this.black = black;
        this.white = white;
        this.winner = winner;
        this.endReason = endReason;
        this.moveCount = moveCount;
        this.finishedAt = Instant.now();
        this.blackMmrBefore = black.getMmr();
        this.whiteMmrBefore = white.getMmr();
    }

    public static Match of(User black, User white, User winner, String endReason, int moveCount) {
        return new Match(black, white, winner, endReason, moveCount);
    }

    public void recordRating(int blackDelta, int whiteDelta) {
        this.rated = true;
        this.blackMmrDelta = blackDelta;
        this.whiteMmrDelta = whiteDelta;
    }
}
