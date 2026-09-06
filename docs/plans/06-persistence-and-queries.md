# 06. 영속성 · 쿼리 최적화

---

## 1. 무엇을 DB에 두어야 하는가

현재 DB에 들어가는 것을 성격별로 나누면 이렇다.

| 데이터 | 수명 | 서버 재시작 시 | 현재 위치 | 적합한 위치 |
| --- | --- | --- | --- | --- |
| 사용자 프로필·점수 | 영구 | 반드시 남아야 함 | MySQL `User` | ✅ DB |
| 전적/기보 | 영구 | 반드시 남아야 함 | **없음** | ✅ DB (신규) |
| 방 목록(로비) | 수초~수분 | 남아 있으면 오히려 **쓰레기** | MySQL `GameRoom` | ❌ 인메모리 |
| 게임 진행 상태 | 한 판 | 남아도 무의미 | `MorrisService`의 `HashMap` 11개 | 인메모리 (구조만 개선) |
| 자체 로그인 계정 | — | — | MySQL `MorrisUser` | ❌ 삭제 (미완성) |

### `GameRoom`을 DB에서 빼야 하는 이유

방 하나의 생애가 `INSERT` → 수십 초 → `DELETE`다.
그런데 이 행은 **서버가 죽으면 정리되지 않는다.**

```java
// MorrisService.java:49, 292, 329, 358 — delete 가 4곳에 흩어져 있고
// P0-6 경로에서는 delete 자체가 실행되지 않는다
gameRoomRepository.delete(gameRoom);
```

[01](01-code-audit.md)의 P0-6에서 본 대로 **게임 시작 전에 나간 사용자의 방은 영구히 남는다.**
`created_at`도 `status`도 없어서 나중에 걸러낼 수도 없다.
게다가 이 데이터의 진짜 원본은 이미 `MorrisService`의 인메모리 맵이다 — **같은 상태를 두 군데 보관**하고 있다.

**반론과 답**

> "서버를 재시작하면 진행 중이던 게임이 날아가지 않나?"

지금도 날아간다. 보드 상태(`gameBoards`)가 메모리에만 있으므로
DB에 방 행이 남아 있어도 복구할 수 없다 — 오히려 **복구 불가능한 방이 목록에 보이는** 상태가 된다.
진짜 복구를 하려면 보드까지 저장해야 하는데, 그건 이 서비스에 필요 없다.

> "서버를 여러 대로 늘리면?"

늘릴 계획이 없다. 사용자 규모를 키울 서비스가 아니다.
단일 인스턴스 전제를 **문서에 명시하고** 인메모리로 간다.
나중에 필요해지면 그때 Redis를 끼우면 되고, 인터페이스만 분리해 두면 교체 비용이 작다.

### 결론

```
DB 테이블: users, matches   ← 이 둘뿐
인메모리 : 방 목록, 게임 상태
```

`GameRoom`, `MorrisUser` 테이블은 삭제한다.

---

## 2. 인메모리 상태를 어떻게 담을 것인가

[01](01-code-audit.md)의 P0-8 — `HashMap` 11개, 동기화 없음, 정리 없음.

```java
// 지금: gameId 하나의 상태가 11개 맵에 흩어져 있어 원자적 갱신이 불가능
private final Map<Long, String[]>  gameBoards = new HashMap<>();
private final Map<Long, Integer>   gamePhases = new HashMap<>();
private final Map<Long, String>    playerStones = new HashMap<>();
// ... 8개 더
```

**목표**

```java
@Component
public class RoomRegistry {
    private final ConcurrentMap<Long, Room> rooms = new ConcurrentHashMap<>();

    /** 방 하나에 대한 모든 변경은 이 메서드를 통해서만 — 방 단위 직렬화 */
    public <R> R mutate(long roomId, Function<Room, R> action) {
        Room room = rooms.get(roomId);
        if (room == null) throw new RoomNotFoundException(roomId);
        synchronized (room) {          // 방 단위 락. 방끼리는 병렬
            return action.apply(room);
        }
    }
}
```

- 맵 1개, 상태 객체 1개. `Room`이 게임 상태([02](02-game-rules-audit.md)의 `MorrisGame`)를 품는다.
- **방 단위 락**이면 서로 다른 방은 병렬로 처리된다. 이 규모에 충분하다.
- 게임 종료 / 전원 퇴장 시 `rooms.remove(roomId)` — **한 곳에서만 정리하면 된다.**
- 유휴 방 청소를 위한 스케줄러 하나 (`@Scheduled`, 예: 30분 이상 미활동 방 제거).
  지금은 이런 청소가 불가능하다 — 정리 대상을 판별할 시각 정보 자체가 없다.

---

## 3. 쿼리별 문제와 개선

### 3-1. 랭킹 조회 — 전체 테이블 스캔
`user/service/UserService.java:41-48`

```java
List<User> users = em.createQuery("SELECT u FROM User u ORDER BY u.score DESC", User.class)
        .getResultList();
return users.stream().map(UserRankDto::new).collect(Collectors.toList());
```

| 문제 | 내용 |
| --- | --- |
| LIMIT 없음 | 전체 행을 메모리로 올린다 |
| 인덱스 없음 | `score` 정렬이 매번 filesort |
| 과다 컬럼 | `email`, `role`까지 로딩하지만 DTO는 3개만 쓴다 |
| `EntityManager` 직접 사용 | 같은 클래스가 `UserRepository`도 쓴다 — 접근 경로 이중화 ([03](03-layering-and-dto.md) ❸) |

**개선** — 인터페이스 프로젝션으로 필요한 컬럼만.

```java
public interface UserRankView {
    String getNickname();
    int    getRating();
    String getImageUrl();
}

public interface UserRepository extends JpaRepository<User, Long> {
    List<UserRankView> findTop100ByProviderNotOrderByRatingDesc(Provider provider);
}
```

게스트를 랭킹에서 제외해야 하므로([08](08-guest-mode-design.md)) 조건이 하나 붙는다.
`EntityManager` 주입은 제거한다.

### 3-2. 방 생성 — 같은 사용자를 두 번 조회
`game/service/GameRoomService.java:30-37, 58-69`

```java
String currentNickname = currentUserNickname();                    // ① findByUserId
User hostUser = userRepository.findByNickname(currentNickname);    // ② 같은 사람을 닉네임으로 다시
```

쿼리 2회. 그리고 **닉네임에 유니크 제약이 없어서** 동명이인이면 ②가 엉뚱한 사용자를 반환한다.
`findByUserId` 한 번이면 끝난다. 로비를 인메모리로 옮기면 이 조회 자체가 방 생성 시 1회로 줄어든다.

### 3-3. 점수 갱신 — SELECT + UPDATE, 락 없음, 트랜잭션 없음
`user/service/UserService.java:55-65`

```java
User user = userRepository.findByUserId(userId);
user.setScore(user.getScore() + score);
userRepository.save(user);
```

- 쿼리 2회, 갱신 손실 가능, 음수 진입 가능, `@Transactional` 없음
- 게임 1판 종료마다 이게 **2번**(승자·패자) 실행되고 서로 다른 트랜잭션이다

**개선** — 단일 원자 UPDATE.

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
       update User u
          set u.rating = u.rating + :delta,
              u.updatedAt = CURRENT_TIMESTAMP
        where u.id = :id
       """)
int addRating(@Param("id") long id, @Param("delta") int delta);
```

하한(0 미만 금지)은 Elo 도입 여부에 따라 달라지므로 [09](09-ranking-design.md)에서 확정한다.
호출부는 승자·패자 갱신 + `Match` 저장을 **하나의 `@Transactional` 안에서** 처리한다.

### 3-4. 인증 필터가 매 요청 DB를 친다
`security/filter/JwtAuthenticationFilter.java:53-54`

```java
Long userId = jwtProvider.extractSubject(token);
User user = userRepository.findByUserId(userId);   // role 하나 얻자고 SELECT
```

역할을 JWT 클레임에 넣으면 **모든 요청에서 쿼리 1회가 사라진다.**
역할 변경 즉시 반영이 필요한 서비스가 아니므로 트레이드오프가 성립한다
(토큰 만료 시간만큼 지연 반영).

### 3-5. 닉네임 조회
`user/service/UserService.java:50-53`

`findNicknameByUserId`가 `User` 전체를 반환한다. 이름과 동작이 다르다([04](04-consistency-and-naming.md) 5-2).
프로젝션으로 바꾸고 `Optional`을 반환한다. 현재는 null이 오면 `getNickname()`에서 NPE다.

### 3-6. N+1 — 지금은 없지만 곧 생긴다

연관관계 매핑이 하나도 없어서 현재 N+1은 발생하지 않는다.
[04](04-consistency-and-naming.md) 4절대로 `@ManyToOne`을 도입하면 `Match` 목록 조회에서 바로 발생한다.

```java
@EntityGraph(attributePaths = {"winner", "loser"})
List<Match> findTop20ByWinnerIdOrLoserIdOrderByFinishedAtDesc(long a, long b);
```

`@ManyToOne`은 **항상 `fetch = LAZY`** 로 두고, 필요한 곳에서만 `@EntityGraph`로 끌어온다.

---

## 4. 인덱스

현재 인덱스가 **PK 외에 하나도 없다.**

| 테이블 | 인덱스 | 이유 |
| --- | --- | --- |
| `users` | `unique (provider, provider_id)` | 카카오 회원번호로 조회 + 중복 가입 방지 |
| `users` | `unique (nickname)` | 3-2의 동명이인 문제. 게스트 닉네임 생성 시에도 필요 |
| `users` | `(rating desc)` | 랭킹 정렬 |
| `users` | `(last_seen_at)` | 유휴 게스트 정리 배치 ([08](08-guest-mode-design.md)) |
| `matches` | `(winner_id, finished_at desc)`, `(loser_id, finished_at desc)` | 내 전적 조회 |

규모가 작아 인덱스 없이도 당장은 느리지 않다.
다만 **유니크 제약은 성능이 아니라 정합성 때문에 반드시 필요하다** —
지금은 닉네임 중복도, 같은 카카오 계정의 중복 행도 DB가 막지 않는다.

---

## 5. 스키마 변경과 마이그레이션

### 가장 큰 변경: `User`의 PK

```java
// user/domain/User.java:16-18 — 카카오 회원번호가 곧 PK다
@Id
@Column(name = "user_id")
private Long userId;
```

**게스트에게는 카카오 회원번호가 없다.** 게스트 모드를 넣으려면 PK를 바꿔야 한다.
이것이 [08](08-guest-mode-design.md)의 선행 작업이고, 마이그레이션 도구가 필요한 진짜 이유다.

```
id (신규 PK, 자동 증가)
provider     KAKAO | GUEST
provider_id  카카오 회원번호 (게스트는 NULL)
unique (provider, provider_id)
```

### Flyway 도입 — **개편이 끝난 뒤 최종 스키마를 V1으로**

현재 마이그레이션 도구가 없고 `ddl-auto`에 의존한다(설정 파일이 없어 값은 미확인).

여기서 흔히 하는 실수가 **지금 스키마를 V1으로 박고 V2~V6으로 고쳐 나가는 것**이다.
이 프로젝트에서는 그럴 이유가 없다.

- 서비스가 이미 내려가 있어 **보존할 운영 데이터가 없다.**
- `User` PK 교체, `GameRoom`·`MorrisUser` 삭제, `matches` 신설 —
  결과적으로 **남는 테이블이 하나도 원형 그대로가 아니다.**
- 아무도 실행한 적 없는 마이그레이션 5개는 이력이 아니라 잡음이다.

**따라서 순서는 이렇다.**

```
1. 개발 중        ddl-auto: create-drop  또는  update  (스키마를 자유롭게 바꿔가며 작업)
2. 구조 확정 후   최종 스키마를 SQL로 덤프 → V1__init.sql 한 장
3. 그 시점부터    ddl-auto: validate  +  Flyway 로 고정
4. 이후 변경만    V2, V3, ... 으로 누적
```

`V1__init.sql`은 이런 형태가 된다 (테이블 2개).

```sql
CREATE TABLE users (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    provider     VARCHAR(20)  NOT NULL,          -- KAKAO | GUEST
    provider_id  VARCHAR(64)  NULL,              -- 카카오 회원번호. 게스트는 NULL
    nickname     VARCHAR(20)  NOT NULL,
    image_url    VARCHAR(500) NULL,
    role         VARCHAR(20)  NOT NULL,
    rating       INT          NOT NULL DEFAULT 1200,
    wins         INT          NOT NULL DEFAULT 0,
    losses       INT          NOT NULL DEFAULT 0,
    draws        INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    last_seen_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_provider (provider, provider_id),
    UNIQUE KEY uk_users_nickname (nickname),
    KEY idx_users_rating (rating DESC),
    KEY idx_users_last_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE matches ( ... );   -- [09](09-ranking-design.md)
```

이후 `ddl-auto: validate`로 고정한다. 엔티티와 스키마가 어긋나면 **부팅이 실패**하게 만든다.
이게 있어야 [04](04-consistency-and-naming.md)의 엔티티 정리가 스키마와 어긋나는 사고를 막는다.

> **주의**: `ddl-auto: create-drop`으로 작업하는 동안에는 **재시작마다 데이터가 날아간다.**
> 로컬 테스트 계정으로만 작업하고, 개발 단계가 끝나는 시점을 명확히 정해 둔다.

---

## 6. 설정 (`application.yml`)

리포지토리에 설정 파일이 없어 현재 값을 확인할 수 없다([01](01-code-audit.md) P2-15).
`application-example.yml`을 만들면서 아래를 함께 반영한다.

```yaml
spring:
  jpa:
    open-in-view: false          # 기본값 true. REST API에 뷰 렌더링이 없는데
                                 # 커넥션을 응답 완료까지 붙들고 있다
    hibernate:
      ddl-auto: validate         # Flyway 도입 후
    properties:
      hibernate:
        jdbc.time_zone: Asia/Seoul
  datasource:
    hikari:
      maximum-pool-size: 5       # 1GiB 인스턴스에서 DB와 동거 → 기본 10은 과하다
      minimum-idle: 2
      connection-timeout: 3000
  flyway:
    enabled: true
```

`open-in-view: false`는 특히 중요하다.
지금 구조에서는 트랜잭션 밖에서 지연 로딩이 일어나도 조용히 동작하는데,
`@ManyToOne(LAZY)`를 도입하면 그 순간 `LazyInitializationException`이 드러난다.
**연관관계 매핑 전에 이 설정을 먼저 켜서 문제를 미리 노출시키는 편이 낫다.**
