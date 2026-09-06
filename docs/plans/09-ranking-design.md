# 09. 점수 · 랭킹 시스템 재설계

---

## 1. 현재 방식의 문제

```java
// MorrisService.java:285-289
userService.increaseScore(winnerId, 30);
userService.decreaseScore(loserId, 20);
```

| 문제 | 내용 |
| --- | --- |
| **승패 가감이 뒤집혀 있다** | 게스트(playerTwo)가 이기면 진 사람이 +30을 받는다 ([01](01-code-audit.md) P0-1) |
| **점수 인플레이션** | 한 판마다 총합이 **+10**씩 늘어난다. 점수가 실력이 아니라 **판수**를 나타낸다 |
| **상대 실력을 반영하지 않는다** | 1200점이 100점을 이겨도, 100점이 1200점을 이겨도 똑같이 +30 |
| **음수로 내려간다** | 0점에서 20점을 잃으면 -20점 |
| **무승부에 규칙이 없다** | 점수 변동 0. 의도인지 누락인지 코드로 알 수 없다 |
| **기권과 정상 패배가 동일** | 둘 다 -20. 기권을 억제할 이유가 없다 |
| **전적이 없다** | 승/패/무 횟수도, 대전 기록도 저장되지 않는다. **점수 하나가 전부다** |
| **"내 등수"를 알 수 없다** | 전체 목록을 받아서 프론트가 세는 수밖에 없다 |

가장 근본적인 건 두 번째다. **점수가 올라간다는 게 "잘한다"가 아니라 "많이 했다"를 뜻한다.**

---

## 2. 결정: Elo 레이팅

## 선택지

| 방식 | 장점 | 단점 | 판정 |
| --- | --- | --- | --- |
| **A.** 현행 승 +30 / 패 -20 | 단순 | 인플레이션. 실력 반영 안 됨 | ❌ |
| **B.** 승점제 (승 3 / 무 1 / 패 0, 누적) | 이해하기 쉬움 | 여전히 판수 = 점수 | ❌ |
| **C.** **Elo** | 제로섬. 상대 실력 반영. 구현이 30줄 | 초반 변동이 큼 | ✅ **채택** |
| **D.** Glicko-2 | 불확실성(RD)까지 모델링. 더 정확 | 구현 복잡. 이 규모에 과함 | ○ 나중에 |

## Elo 계산

```java
// morris-core — Spring 의존 없는 순수 함수. 테스트하기 쉽다.
public final class Elo {
    public static final int INITIAL = 1200;

    /** actualScore: 승 1.0, 무 0.5, 패 0.0 */
    public static int delta(int myRating, int opponentRating, double actualScore, int k) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentRating - myRating) / 400.0));
        return (int) Math.round(k * (actualScore - expected));
    }
}
```

| 파라미터 | 값 | 근거 |
| --- | --- | --- |
| 초기 레이팅 | **1200** | 관례 |
| K 계수 | **32** (30판 미만은 **48**) | 신규 사용자가 빨리 제자리를 찾게 한다 |
| 하한 | **100** | 음수 방지. 0으로 두면 "0점"이 최하위라는 의미가 모호해진다 |
| 무승부 | 양쪽 `actualScore = 0.5` | 레이팅이 낮은 쪽이 소폭 이득 — 정상 동작이다 |

**제로섬이다.** 한쪽이 얻는 만큼 다른 쪽이 잃으므로 총합이 보존되고 인플레이션이 없다.

### 기권 처리

**기권은 패배와 동일하게 계산한다.** 별도 페널티를 주지 않는다.

- 추가 페널티를 주면 "질 것 같으면 창을 닫는다"가 더 유리해진다 — 역효과다.
- 대신 **연결 끊김을 기권으로 처리할지**가 진짜 문제다.
  현재는 소켓이 끊기면 방을 지우고 끝이다([01](01-code-audit.md) P0-6). 점수 변동이 없다.
  → **30초 재접속 유예**를 두고, 그 안에 안 돌아오면 기권 처리한다.
  유예가 없으면 지는 쪽이 랜선을 뽑는 게 최적 전략이 된다.

### 랭크전 / 일반전

**게스트가 낀 게임은 레이팅을 변동시키지 않는다.**
근거는 [08](08-guest-mode-design.md) 4절 — 게스트를 무한히 만들어 본계정에 점수를 몰아줄 수 있다.

```
회원 vs 회원      →  랭크전.  Elo 변동 O,  matches 기록 O
게스트가 낀 게임   →  일반전.  Elo 변동 X,  matches 기록 X
```

---

## 3. 스키마

`users`에 집계값, `matches`에 원장(ledger)을 둔다.
집계값은 언제든 `matches`로부터 재계산할 수 있어야 한다.

```sql
-- users (일부)
rating       INT NOT NULL DEFAULT 1200,
wins         INT NOT NULL DEFAULT 0,
losses       INT NOT NULL DEFAULT 0,
draws        INT NOT NULL DEFAULT 0,
peak_rating  INT NOT NULL DEFAULT 1200,   -- 최고 기록. 표시용
KEY idx_users_rating (rating DESC)
```

```sql
CREATE TABLE matches (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    black_id            BIGINT      NOT NULL,
    white_id            BIGINT      NOT NULL,
    winner_id           BIGINT      NULL,          -- NULL = 무승부
    end_reason          VARCHAR(20) NOT NULL,      -- NORMAL | RESIGN | DRAW_AGREED | TIMEOUT | DISCONNECT
    ranked              BOOLEAN     NOT NULL,      -- 랭크전 여부
    black_rating_before INT         NOT NULL,
    white_rating_before INT         NOT NULL,
    black_rating_delta  INT         NOT NULL,
    white_rating_delta  INT         NOT NULL,
    move_count          INT         NOT NULL,
    started_at          DATETIME(6) NOT NULL,
    finished_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_matches_black (black_id, finished_at DESC),
    KEY idx_matches_white (white_id, finished_at DESC),
    CONSTRAINT fk_matches_black FOREIGN KEY (black_id) REFERENCES users(id),
    CONSTRAINT fk_matches_white FOREIGN KEY (white_id) REFERENCES users(id)
);
```

- `black_id` / `white_id`로 저장한다. `winner`/`loser`로 저장하면 **무승부를 표현할 수 없다.**
- `*_rating_before`와 `*_rating_delta`를 둘 다 저장한다 →
  나중에 K 계수를 바꿔도 **과거 경기를 그대로 재계산**할 수 있다.
- 엔티티는 [04](04-consistency-and-naming.md) 4절 정책대로 `@ManyToOne(LAZY)` 두 개만.
  `User`에 `@OneToMany List<Match>`는 만들지 않는다.

---

## 4. 갱신 흐름

```
게임 종료 (morris-core 가 Outcome 반환)
        │
        ▼
  GameFinishedEvent 발행          ← [03](03-layering-and-dto.md) ❺: 게임 엔진이 점수를 직접 안 건드린다
        │
        ▼
  RankingListener  @Transactional
        ├─ 랭크전이 아니면 여기서 종료
        ├─ Elo delta 계산 (morris-core 의 순수 함수)
        ├─ users UPDATE ×2   (원자적 단일 쿼리)
        ├─ matches INSERT ×1
        └─ Redis ZADD ×2     (실패해도 무시. DB가 정본)
```

**전부 한 트랜잭션이다.** 현재는 승자 +30과 패자 -20이 별개 트랜잭션이라
한쪽만 커밋되는 상태가 가능하다([04](04-consistency-and-naming.md) 2절).

```java
@Modifying(clearAutomatically = true)
@Query("""
       update User u
          set u.rating      = greatest(:floor, u.rating + :delta),
              u.peakRating  = greatest(u.peakRating, u.rating + :delta),
              u.wins        = u.wins   + :w,
              u.losses      = u.losses + :l,
              u.draws       = u.draws  + :d
        where u.id = :id
       """)
int applyResult(@Param("id") long id, @Param("delta") int delta,
                @Param("floor") int floor,
                @Param("w") int w, @Param("l") int l, @Param("d") int d);
```

read-modify-write 대신 단일 UPDATE — 갱신 손실이 사라진다([06](06-persistence-and-queries.md) 3-3).

---

## 5. Redis Sorted Set — "내 등수"

```
ZADD      leaderboard {rating} {userId}
ZREVRANGE leaderboard 0 99 WITHSCORES     상위 100
ZREVRANK  leaderboard {userId}            내 등수    ← 지금은 아예 없는 기능
ZCARD     leaderboard                     전체 인원
```

- **MySQL이 정본, Redis는 읽기 뷰**다. 부팅 시 DB에서 ZSET을 재구성한다.
  Redis가 날아가도 데이터를 잃지 않는다.
- `ZREVRANK`는 O(log N)이다. SQL로 등수를 구하려면 윈도우 함수나 카운트 쿼리가 필요하다.
- 게스트는 ZSET에 넣지 않는다.
- Redis 장애 시 DB 쿼리로 폴백한다. 랭킹 때문에 게임이 안 되면 안 된다.

---

## 6. 확장 여지 (지금 안 만들어도 되지만 자리를 남겨 둔다)

| 기능 | 필요한 것 | 비고 |
| --- | --- | --- |
| 최근 전적 | `matches` 조회 | 스키마만 있으면 바로 됨 |
| 승률 · 연승 | `wins/losses/draws` + 최근 N경기 | |
| 티어 (브론즈~다이아) | `rating` 구간 매핑 | 숫자보다 동기부여가 크다 |
| 시즌 | `season_id` 컬럼 + 시즌 종료 시 레이팅 소프트 리셋 | 6개월 운영이면 시즌 1개로 충분 |
| 기보 저장 / 리플레이 | `matches.moves` (JSON) | [02](02-game-rules-audit.md) H. 착수 로그를 남기면 분쟁 검증도 된다 |
| 상대 전적 | `matches` 두 사람 필터 | |
| 최고 레이팅 | `peak_rating` | 이미 스키마에 넣어 뒀다 |

**티어 표시**를 우선 추천한다. 구현 비용이 매핑 테이블 하나인데,
"1237점"보다 "실버 II"가 사용자에게 훨씬 잘 읽힌다.

---

## 7. API

| 엔드포인트 | 내용 |
| --- | --- |
| `GET /api/v1/rankings?limit=100` | 상위 N명 (닉네임, 레이팅, 티어, 프로필) |
| `GET /api/v1/rankings/me` | **내 등수 · 레이팅 · 승패무 · 티어** (신규) |
| `GET /api/v1/users/{id}/matches?size=20` | 최근 전적 (신규) |

현재 `GET /api/rank` 하나뿐이고 전체 목록을 반환한다([05](05-api-and-protocol.md), [06](06-persistence-and-queries.md) 3-1).

---

## 8. 마이그레이션

기존 `score` 데이터는 **버린다.**

- 승 +30 / 패 -20 누적값이라 **Elo로 환산할 의미 있는 방법이 없다.**
  판수를 나타내는 수치를 실력 수치로 변환할 수는 없다.
- 어차피 서비스가 내려가 있어 보존 대상이 없다([06](06-persistence-and-queries.md) 5절).
- 전원 `rating = 1200`으로 시작한다.
