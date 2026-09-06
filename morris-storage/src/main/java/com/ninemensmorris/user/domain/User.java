package com.ninemensmorris.user.domain;

import com.ninemensmorris.core.rating.Rating;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카카오 회원번호를 그대로 기본키로 쓰고 있음
// 게스트 로그인을 넣으려면 대체키로 바꿔야 하며 그때 provider / providerId 로 분리함
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String email;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private int mmr;

    // 최고 기록. 떨어져도 남는 값이라 동기부여가 됨
    @Column(nullable = false)
    private int peakMmr;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int draws;

    private User(Long userId, String email, String nickname, String imageUrl, String role) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.imageUrl = imageUrl;
        this.role = role;
        this.mmr = Rating.INITIAL;
        this.peakMmr = Rating.INITIAL;
    }

    public static User ofKakao(Long kakaoId, String email, String nickname, String imageUrl) {
        return new User(kakaoId, email, nickname, imageUrl, "ROLE_USER");
    }

    public int gamesPlayed() {
        return wins + losses + draws;
    }

    // 세터 대신 의도가 드러나는 메서드로만 바꾼다
    // 하한과 최고 기록 갱신을 엔티티가 보장해야 호출부마다 빠뜨리지 않는다
    public void applyMatchResult(int mmrDelta, MatchOutcome outcome) {
        this.mmr = Rating.applyFloor(this.mmr + mmrDelta);
        this.peakMmr = Math.max(this.peakMmr, this.mmr);
        switch (outcome) {
            case WIN -> this.wins++;
            case LOSS -> this.losses++;
            case DRAW -> this.draws++;
        }
    }

    public void changeProfile(String nickname, String imageUrl) {
        this.nickname = nickname;
        this.imageUrl = imageUrl;
    }

    public enum MatchOutcome {
        WIN,
        LOSS,
        DRAW
    }
}
