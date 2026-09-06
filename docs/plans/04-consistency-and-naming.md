# 04. 컨벤션 불일치 · 네이밍 · 데드코드 · JPA 연관관계 정책

---

## 1. 객체 생성 방식이 4가지로 갈려 있다

| 방식 | 적용된 클래스 |
| --- | --- |
| `@Builder` | `MorrisUser`, `User`, `SignUpResponseDto`, `CreateGameResponseDto`, `UserResponseDto`, `StonePlacementResponseDto`, `MorrisResponse`, `CustomErrorResponse` |
| `new` + setter | `GameRoom` (조립은 `GameRoomService.java:64-69`) |
| 손수 쓴 생성자 | `GameRoomDto`, `UserRankDto`, `UserNicknameResponseDto` |
| 애너테이션 없음 (Jackson 리플렉션 주입) | `StonePlacementRequestDto`, `RemoveOpponentStoneRequestDto`, `TieRequestDto`, `WithdrawRequestDto`, `SignUpRequestDto` |
| `@Getter @Setter @NoArgsConstructor` | `CreateGameRequestDto` — 요청 DTO 중 혼자 다름 |

요청 DTO 6개가 **서로 다른 애너테이션 조합**을 쓴다.
생성자도 세터도 없는 4개는 Jackson이 게터로 프로퍼티를 추론한 뒤 필드에 리플렉션으로 꽂아 넣는다.
동작은 하지만 "왜 되는지"가 코드에 안 드러난다.

### 정책 (제안)

| 종류 | 방식 |
| --- | --- |
| 요청 DTO | `record` — 불변, 컴팩트 생성자에서 형식 검증, Jackson이 정식 지원 |
| 응답 DTO | `record` + 정적 팩터리 `from(...)` |
| Command | `record` ([03](03-layering-and-dto.md)) |
| 엔티티 | `@NoArgsConstructor(PROTECTED)` + **정적 팩터리** (아래 3절) |

`@Builder`는 **필드가 5개 이상이고 선택적 필드가 섞인 경우에만** 쓴다.
`CreateGameResponseDto`(3필드)에 빌더는 과하다.

엔티티에 `@Builder` + `@AllArgsConstructor`를 붙이는 지금 방식(`User`, `MorrisUser`)은
**필드 순서를 바꾸면 조용히 깨진다.** 엔티티에서는 걷어낸다.

---

## 2. `@Transactional` 적용이 제각각이다

| 클래스 | 현재 선언 | 쓰기 작업 | 판정 |
| --- | --- | --- | --- |
| `AuthService` | 클래스 `@Transactional` | `save` | ⚠️ 조회/쓰기 구분 없음 |
| `GameRoomService` | 클래스 `readOnly=true` + 쓰기 메서드 `@Transactional` | `save`, `delete` | ✅ 올바른 패턴 |
| `UserService` | **없음** | `save` ×2 (`increaseScore`, `decreaseScore`) | ❌ |
| `MorrisService` | **없음** | `delete` ×4 + `UserService` 호출 | ❌ |
| `CustomOAuth2UserService` | **없음** | `save` (신규 가입) | ❌ |

**실제 영향**

`MorrisService.handleMorrisResult()`는 트랜잭션 밖에서
`increaseScore` → `decreaseScore` → `delete`를 순서대로 부른다.
각각이 별개 트랜잭션이므로 **승자 +30만 커밋되고 패자 -20이 실패하는 상태**가 가능하다.
점수 총합이 조용히 어긋난다.

### 정책

> 서비스 클래스에 `@Transactional(readOnly = true)`, 상태를 바꾸는 메서드에만 `@Transactional`.
> `org.springframework.transaction.annotation.Transactional`을 쓴다 (jakarta 것 아님).

`GameRoomService`가 이미 이 패턴이므로 나머지를 맞추면 된다.
`MorrisService`는 게임 종료 처리 전체가 하나의 트랜잭션이어야 한다.

---

## 3. 엔티티에 행위가 없다 (Anemic Domain Model)

`User`, `MorrisUser`, `GameRoom` — **세 엔티티 모두 `@Getter @Setter`만 있고 메서드가 0개**다.
반대로 엔티티가 아닌 `MorrisStatus`에는 메서드가 3개 있고,
`GameRoomDto` / `UserRankDto`에는 매핑 생성자가 있다. 규칙이 없다.

### 문제

```java
// user/service/UserService.java:56-58 — 상태 변경 로직이 서비스에 흩어져 있다
User user = userRepository.findByUserId(userId);
user.setScore(user.getScore() + score);
userRepository.save(user);
```

`setScore`는 **어떤 값이든 받는다.** 음수 방지, 상한, 시즌 규칙을 넣을 자리가 없다.
실제로 점수가 음수로 내려간다([01](01-code-audit.md)의 P2-1).

`GameRoomService.java:64-69`도 마찬가지로 세터 6번으로 방을 조립한다 —
**불완전한 상태의 엔티티가 존재할 수 있는 구간**이 생긴다.

### 정책

1. **엔티티에서 `@Setter`를 전면 제거한다.**
2. 상태 변경은 의도가 드러나는 메서드로만.
   ```java
   public void gainScore(int points) {
       if (points < 0) throw new IllegalArgumentException("points must be >= 0");
       this.score += points;
   }
   public void loseScore(int points) {
       this.score = Math.max(0, this.score - points);   // 하한을 엔티티가 보장
   }
   public void changeNickname(String nickname) { ... }
   ```
3. 생성은 **정적 팩터리**로. 불완전한 엔티티가 만들어질 수 없게 한다.
   ```java
   public static User ofKakao(long kakaoId, String nickname, String imageUrl) { ... }
   public static User guest(String nickname) { ... }
   ```
4. `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 **모든 엔티티에** 명시.
   `GameRoom`은 지금 이것조차 없어서 자바 기본 생성자에 의존하고 있다.
5. `equals`/`hashCode`를 id 기준으로 정의하거나, "정의하지 않는다"를 명시적으로 결정한다.
   현재는 결정 자체가 없다.

---

## 4. JPA 연관관계 정책 — `@ManyToOne` 외에는 쓰지 않는다

### 현재 상태: 연관관계 매핑이 **하나도 없다**

전부 raw `Long`/`String` 컬럼이다.

```java
// game/domain/GameRoom.java
private String roomTitle;
private String host;           // User.nickname 문자열 복사
private int    hostScore;      // User.score 복사   ← 방 생성 시점 스냅샷
private String hostImageUrl;   // User.imageUrl 복사
private Long   playerOneId;    // host 와 같은 사람의 id
private Long   playerTwoId;
```

정책("`@ManyToOne`만 허용")에 **표면적으로는 부합하지만**, 실제로는 정규화를 포기한 결과다.

| 문제 | 내용 |
| --- | --- |
| 같은 사람을 두 컬럼에 중복 저장 | `host`(닉네임)와 `playerOneId`(id)가 동일인 |
| `hostScore`가 즉시 낡는다 | 방 생성 시점 스냅샷. 게임을 이겨도 방 목록 점수는 그대로 |
| FK 제약이 없다 | 탈퇴한 사용자 id가 그대로 남는다 |
| 닉네임으로 사용자를 되찾는다 | `findByNickname` — 유니크 제약이 없어 동명이인이면 엉뚱한 사용자 |

### 목표

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "player_one_id", nullable = false)
private User playerOne;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "player_two_id")
private User playerTwo;
```

- `host`, `hostScore`, `hostImageUrl` **3개 컬럼 삭제** — `playerOne`에서 파생시킨다.
- `User`에는 `@OneToMany List<GameRoom>`을 **넣지 않는다.** 정책대로.
  방 목록이 필요하면 `GameRoomRepository`에 쿼리를 둔다.
- `@ManyToOne`은 **항상 `fetch = LAZY`**. 기본값이 `EAGER`라 명시하지 않으면 N+1의 근원이 된다.
- 조회 시 N+1은 `@EntityGraph` 또는 DTO 프로젝션으로 해결한다 ([06](06-persistence-and-queries.md)).

### 다만 — `GameRoom` 자체가 사라질 가능성이 높다

[06](06-persistence-and-queries.md)에서 **로비(방 목록)를 DB에서 빼는 방향**을 제안한다.
그러면 위 논의는 `GameRoom`에는 적용되지 않고, 대신 새로 만들 `Match`(전적) 엔티티에 적용된다.

```java
@Entity
public class Match {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;

    @ManyToOne(fetch = LAZY) @JoinColumn(name = "winner_id") private User winner;
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "loser_id")  private User loser;

    @Enumerated(EnumType.STRING) private MatchResult result;   // WIN / DRAW / RESIGN
    private int    winnerRatingDelta;
    private int    loserRatingDelta;
    private Instant startedAt;
    private Instant finishedAt;
}
```

`User`에 `@OneToMany List<Match>`는 만들지 않는다.
"내 전적"은 `matchRepository.findByWinnerIdOrLoserId(...)`로 조회한다.

---

## 5. 네이밍

### 5-1. 같은 대상을 두 어휘로 부른다 — 가장 혼란스러운 지점

| 어휘 A | 어휘 B | 가리키는 대상 |
| --- | --- | --- |
| `playerOneId` / `playerTwoId` | `hostTotal` / `guestTotal`, `hostAddableStones` / `guestAddableStones` | 같은 두 사람 |
| `host` (String 닉네임) | `playerOneId` (Long id) | 같은 한 사람 |
| `PLAYER_ONE_STONE = "BLACK"` | — | 방장이 항상 흑돌이라는 암묵 전제 |

`MorrisService` 안에서 `host/guest`와 `playerOne/playerTwo`가 뒤섞여 나온다.
[02](02-game-rules-audit.md)의 H-1(선공 랜덤화)을 구현하려면 **"방장"과 "흑돌"을 반드시 분리**해야 하는데,
지금 어휘가 그 둘을 같은 것으로 취급하고 있어서 먼저 정리하지 않으면 손댈 수 없다.

**제안**: 역할은 `host` / `guest`, 게임 내 위치는 `black` / `white`로 통일하고 둘을 매핑으로 연결한다.

### 5-2. 이름이 동작과 반대이거나 모호한 것

| 현재 | 문제 | 제안 |
| --- | --- | --- |
| `MorrisResponseCode.CANNOT_REMOVE` | 메시지가 `"3개 연속입니다. 돌을 제거하세요."` — **제거하라는 뜻인데 이름은 못 한다는 뜻** | `MILL_FORMED` |
| `MorrisResponseCode.THREE_IN_A_ROW` | 위와 메시지가 완전히 동일한데 **미사용**. 올바른 이름이 이미 있는데 안 쓴다 | 위와 통합 |
| `checkRemovalConditions()` | 실제로는 "밀이 만들어졌는가"를 반환 | `isMillFormed()` |
| `checkRowOrColumnTriples()` | `true` = "제거 불가". 이름에서 유추 불가 | `isProtectedByMill()` |
| `isAllOpponentStones()` | 주석이 `return false; // 제거 가능` / `return true; // 제거 불가능` — **이름과 주석이 서로 다른 얘기** | `isEntireMill()` |
| `rowTriples` / `columnTriples` | 도메인 용어는 "mill"이다. 게다가 `static final`인데 소문자 | `HORIZONTAL_MILLS` / `VERTICAL_MILLS` |
| `decreaseAddableStones()` | 차감 외에 **단계 전환이라는 부수효과**가 있다 | `consumeStoneAndAdvancePhase()` 또는 분리 |
| `StonePlacementResponseDto` | 착수뿐 아니라 시작·제거·기권·종료 응답으로 전부 재사용된다 | `GameStateResponse` |
| `StonePlacementRequestDto.initialPosition` | 1단계에서는 "출발지"가 아니라 "놓을 자리"다. `finalPosition`은 미사용 | `PlaceCommand(to)` / `SlideCommand(from, to)` 로 분리 |
| `UserRepository.findNicknameByUserId()` | `User` 전체를 반환한다. 닉네임만 주는 것처럼 읽힌다 | 삭제하고 `findByUserId` 재사용 |
| `MorrisUser` vs `User` | 사용자 엔티티가 두 개. 어느 쪽이 진짜인지 이름으로 알 수 없다 | `MorrisUser` 삭제 (7절) |
| `MorrisStatus.Status` | `MorrisStatus`라는 껍데기 클래스 안의 `Status` — `MorrisStatus.Status.PLAYING` | 최상위 `GameStatus` enum |
| `removePosition == 99` | 매직 넘버로 "제거 건너뛰기"를 표현 | 별도 메시지 매핑 |
| `MorrisResponse` | 응답이 아니라 **브로드캐스트 이벤트**다 | `GameEvent` |

### 5-3. 자잘한 것

- `game/dto/GameRoom/`, `game/dto/Morris/` — **패키지명이 대문자로 시작한다.** 자바 관례 위반 → `gameroom`, `morris`
- `build.gradle:7` — `group = 'com'` → `com.ninemensmorris`
- `UserRepository.java:10` — `findByUserId(Long UserId)` 파라미터명이 대문자 시작
- `MorrisResponseCode.java:22` — `private String message;` `final` 아님
- `CustomException.java:10` — `ErrorCode errorCode;` 접근 제어자 없음(package-private)
- `JwtAuthenticationFilter.java:36`, `OAuth2SuccessHandler.java:30` — `doFilterInternal (` 메서드명과 괄호 사이 공백
- 들여쓰기 혼재 — `NineMensMorrisApplication.java`와 `build.gradle`은 탭, 나머지는 스페이스 4칸
- `OAuth2SuccessHandler.java:42` — `// 240404 ldhbenecia | https 설정 이후 사용` 주석에 날짜·작성자 서명. `git blame`이 하는 일이다
- **포맷을 강제할 수단이 없다** — `.editorconfig`, Spotless, Checkstyle 전부 없음

---

## 6. 데드코드 (삭제 대상)

| 대상 | 위치 | 비고 |
| --- | --- | --- |
| `MorrisStatus` 클래스 본체 | `game/domain/MorrisStatus.java:9-32` | 중첩 enum만 쓰인다. `printStatus()`의 `System.out.println` 2줄 포함 — 커밋 `4a83ad1`에서 sout 제거를 했는데 여기만 남았다 |
| `LogoutService` | `auth/service/LogoutService.java` | 몸통이 빈 `LogoutHandler`. `SecurityConfig`에 주입까지 되어 있다 |
| `MorrisResponseCode.THREE_IN_A_ROW` | | 미사용 |
| `ResponseType.SYNC_GAME`, `ERROR` | `MorrisResponse.java:31` | 미사용. 특히 `SYNC_GAME`은 재접속 기능이 없다는 증거 |
| `ErrorCode.SYSTEM_EXCEPTION`, `NOT_FOUND_HANDLER`, `NOT_FOUND_SESSION_ID` | | 미사용 |
| `JwtProvider.generateRefreshToken()` | `JwtProvider.java:57-59` | 호출처 없음 |
| `JwtProvider`의 `@Value` 필드 3개 | `JwtProvider.java:27-34` | `refreshTokenExpirationPeriod`, `accessHeader`, `refreshHeader` 전부 미사용. **그런데 없으면 앱이 안 뜬다** — 불필요한 환경변수 요구 |
| `JwtProvider`의 `@Getter` | `JwtProvider.java:17` | 시크릿 키 게터를 공개한다 |
| 주석을 감싼 try/catch | `CustomOAuth2UserService.java:29-33` | 유일한 내용이 주석 처리된 `System.out.println` |
| `createUserFromOAuth2User`의 null 재확인 | `CustomOAuth2UserService.java:58-61` | 호출자가 이미 null 확인 후 부른다 |
| `socketUserMap` | `MorrisService.java:43, 55-61` | `put`/`remove`만 하고 **읽는 곳이 없다** |
| 자체 로그인 일체 | `MorrisUser`, `MorrisUserRepository`, `AuthService`, `AuthController`, `SignUpRequestDto`, `SignUpResponseDto` | 미완성. 로그인 엔드포인트 자체가 없다. [08](08-guest-mode-design.md)이 대체 |
| `spring-boot-starter-thymeleaf`, `thymeleaf-extras-springsecurity6` | `build.gradle:24, 26` | 템플릿이 0개다 |
| `src/main/resources/static/index.html` | | 카카오 로그인 링크 한 줄짜리 테스트 페이지. 프론트가 GitHub Pages면 불필요 |
| `UserRepository.findByEmail`, `findNicknameByUserId` | | 호출처 없음 / 중복 |

---

## 7. 정리 순서

네이밍·데드코드 정리는 **기능 변경과 섞으면 리뷰가 불가능해진다.** 분리해서 진행한다.

1. **삭제만 하는 커밋** — 6절 데드코드. 컴파일만 통과하면 된다. 리뷰 부담 0.
2. **`.editorconfig` + Spotless 도입 후 포맷 일괄 적용** — 이것도 단독 커밋.
3. **패키지/클래스 리네임** — IDE 리팩터링 기능으로. 단독 커밋.
4. **엔티티 `@Setter` 제거 + 정적 팩터리** — 여기서부터 실제 동작이 바뀐다. 테스트가 있어야 한다.
5. **`@Transactional` 정책 적용**.
6. **연관관계 매핑 전환** — [06](06-persistence-and-queries.md)의 스키마 결정 이후에.
