# 03. 계층 독립도와 DTO 경계

## 0. 결론 먼저

현재 의존 방향이 여러 군데서 거꾸로거나 계층을 건너뛴다.
그중 **요청 DTO를 컨트롤러에서 서비스까지 그대로 흘려보내는 관행**은
이 프로젝트에서 실제 보안 취약점([01](01-code-audit.md)의 P0-2)을 만들어냈다.
"일반적인 방식"인 건 맞지만, **이 코드베이스에서는 그 관행이 이미 사고를 냈다.**

---

## 1. 현재 의존 방향 (문제 지점)

```
                    ┌─────────────────────────────────┐
                    │  SimpMessagingTemplate (전송)    │
                    └──────────────▲──────────────────┘
                                   │ ❶ 서비스가 전송 계층을 안다
  Controller ──────────────▶  MorrisService ──────▶ GameRoomRepository  ❹
      │                            │
      │                            └──────────────▶ UserService  ❺
      │
      └──────────────────────▶  GameRoomService ───▶ SecurityContextHolder  ❷
                                       │
                                       └──────────▶ UserRepository (남의 애그리거트)  ❻
  UserService ─────────────▶ EntityManager  ❸

  GameRoomDto ─────────────▶ GameRoom (엔티티)   ❼  ← 화살표가 거꾸로
```

### ❶ 서비스가 전송 계층을 직접 안다
`game/service/MorrisService.java:28, 53`

```java
private final SimpMessagingTemplate simpMessagingTemplate;
...
simpMessagingTemplate.convertAndSend("/topic/game/" + gameRoom.getId(), "SOCKET_ERROR");
```

게임 규칙 서비스가 STOMP 토픽 경로 문자열을 알고 있다.
`MorrisController`도 같은 `SimpMessagingTemplate`을 갖고 있어서 **전송 책임이 두 곳에 흩어져 있다.**
테스트하려면 목(mock) 메시징 템플릿이 필요하다.

→ 서비스는 결과를 **반환**하고, 브로드캐스트는 컨트롤러(또는 이벤트 리스너) 한 곳에서만.

### ❷ 서비스가 `SecurityContextHolder`를 직접 읽는다
`user/service/UserService.java:26-27`, `game/service/GameRoomService.java:31-32, 59-60, 101-102`

```java
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
String currentUserId = authentication.getName();
```

세 가지가 동시에 잘못된다.

1. 서비스가 웹/보안 계층에 의존한다. 단위 테스트마다 `SecurityContextHolder`를 세팅해야 한다.
2. **`SecurityContextHolder`는 ThreadLocal이다.** 서블릿 필터가 채워주는 값이라
   **STOMP 메시지 처리 스레드에서는 비어 있다.** 지금은 이 메서드들이 REST로만 호출되어
   우연히 동작하지만, 게임 로직을 STOMP로 옮기는 순간 깨진다.
3. 비인증 요청이면 `getName()`이 `"anonymousUser"`를 반환하고
   `Long.parseLong("anonymousUser")` → `NumberFormatException` → 401이 아니라 **500**이 나간다.
   게스트 모드를 넣을 때 반드시 걸리는 지점이다.

→ **호출자(actor)는 파라미터로 받는다.** 서비스가 스스로 캐내지 않는다.

### ❸ 서비스가 `EntityManager`를 직접 쓴다
`user/service/UserService.java:22, 42-43`

```java
private final EntityManager em;
List<User> users = em.createQuery("SELECT u FROM User u ORDER BY u.score DESC", User.class)
```

JPQL 문자열이 서비스에 노출되어 있다. 같은 클래스가 `UserRepository`도 쓰고 있어 **접근 경로가 두 가지**다.
→ 리포지토리로 일원화. 상세는 [06](06-persistence-and-queries.md).

### ❹ 게임 엔진이 DB를 직접 지운다
`game/service/MorrisService.java:49, 292, 329, 358`

`gameRoomRepository.delete(gameRoom)`이 4곳에 흩어져 있다.
게임 종료 판정과 방 정리는 다른 관심사다. 게다가 `MorrisService`에는 `@Transactional`이 없어
이 delete들은 각자 별개의 트랜잭션으로 커밋된다.

### ❺ 게임 종료가 점수 갱신에 직접 결합되어 있다
`game/service/MorrisService.java:285-289, 326-327`

`MorrisService` → `UserService.increaseScore()` 직접 호출.
랭킹 정책을 바꾸려면([09](09-ranking-design.md)에서 Elo 도입 예정) 게임 엔진을 건드려야 한다.
→ `GameFinishedEvent`를 발행하고 랭킹 쪽이 구독하는 편이 낫다.
이 프로젝트 규모에서는 `ApplicationEventPublisher`로 충분하다.

### ❻ 다른 애그리거트의 리포지토리를 직접 조회한다
`game/service/GameRoomService.java:28, 34, 62`

`GameRoomService`가 `UserRepository`를 직접 쓴다.
`User`가 필요한 이유는 방 목록에 표시할 닉네임/점수/이미지 때문인데,
이건 조회 전용 관심사라 **조회 전용 DTO 프로젝션**으로 푸는 게 맞다([06](06-persistence-and-queries.md)).

### ❼ DTO가 엔티티를 의존한다 (화살표가 거꾸로)
`game/dto/GameRoom/GameRoomDto.java:16`, `user/dto/UserRankDto.java:13`

```java
public GameRoomDto(GameRoom gameRoom, int playerCount) {   // 표현 계층이 도메인을 컴파일 의존
    this.roomId = gameRoom.getId();
    ...
}
```

`GameRoom` 필드명을 바꾸면 표현 계층이 깨진다.
그리고 `UserResponseDto`는 반대로 서비스에서 빌더로 조립한다 — **같은 일을 두 방식으로 한다**
([04](04-consistency-and-naming.md) 참고).

→ 매핑 책임을 한쪽으로 고정한다. 권장은 **정적 팩터리를 DTO에 두되 엔티티가 아니라 값을 받는 것**,
또는 리포지토리에서 아예 DTO로 프로젝션해 오는 것.

### ❽ 컨트롤러가 도메인 판단을 한다
`game/controller/GameRoomController.java:36-41`, `user/controller/UserController.java:38-42`

```java
boolean success = gameRoomService.joinGame(roomId, userId);
if (success) { return ResponseEntity.ok(...); }
else { throw new CustomException(ErrorCode.FAILED_TO_JOIN_GAME); }
```

`boolean` 반환 자체가 신호다. 서비스는 이미 다른 경로에서는 `CustomException`을 던지면서
어떤 실패는 `false`로 돌려준다 — **실패 표현이 두 가지**다.
게다가 `false`에는 이유가 없어서 컨트롤러가 뭉뚱그린 에러 코드를 붙인다.

→ 서비스가 실패 이유를 담아 예외를 던지거나, 결과 타입(`sealed interface`)을 반환한다.
컨트롤러는 매핑만 한다.

### ❾ 응답 DTO가 도메인 enum을 그대로 노출한다
`game/dto/Morris/StonePlacementResponseDto.java:23`

```java
private MorrisStatus.Status status;   // 도메인 enum이 그대로 JSON 스펙이 됨
```

도메인 enum에 값을 추가하면 API 스펙이 바뀐다. 표현 계층 전용 enum으로 변환해야 한다.

---

## 2. 요청 DTO를 서비스까지 넘기는 문제

질문하신 부분이다. 결론부터: **이 프로젝트에서는 바꿔야 한다.**

### 왜 지금 구조가 사고를 냈나

```java
// game/controller/MorrisController.java:51-55
@MessageMapping("/game/withdraw")
public void withdraw(WithdrawRequestDto requestDto) {
    MorrisResponse<...> r = morrisService.withdraw(requestDto);   // 클라이언트가 만든 객체를 그대로
}

// game/dto/Morris/WithdrawRequestDto.java
public class WithdrawRequestDto {
    private Long gameId;
    private Long userId;    // ← 이 필드는 "클라이언트가 주장하는 값"이다
}

// game/service/MorrisService.java:319
Long userId = requestDto.getUserId();   // ← 서비스는 이걸 "확정된 사실"로 취급한다
```

같은 객체 안에 **클라이언트가 주장한 값**과 **서버가 확정해야 할 값**이 섞여 있다.
타입만 봐서는 구분할 수 없고, 서비스 작성자는 자연히 신뢰한다.
그 결과가 P0-2 — 상대의 `userId`를 넣어 보내면 상대가 기권한 것으로 처리된다.

반대로 `GameRoomService`는 이 문제를 피하려고 `SecurityContextHolder`를 직접 뒤진다(❷).
**같은 문제에 서로 다른, 둘 다 잘못된 대응을 한 것**이다.

### 최소 규칙 2개

무거운 헥사고날 구조를 도입하자는 얘기가 아니다. 규칙 두 개면 된다.

> **규칙 1. 서비스는 `@RequestBody`/`@Payload`로 바인딩되는 타입을 파라미터로 받지 않는다.**
> **규칙 2. 서비스 입력 타입(Command)의 첫 인자는 항상 서버가 확정한 `actorId`다.**

```java
// web 계층 — Jackson 바인딩 전용. 신뢰할 수 없는 값만 담는다.
public record WithdrawRequest(Long gameId) {}

// service 계층 — 서버가 조립한다. 클라이언트는 이 타입을 만들 수 없다.
public record WithdrawCommand(long actorId, long gameId) {
    public WithdrawCommand {
        if (actorId <= 0) throw new IllegalArgumentException("actorId");
    }
}

// controller — 유일하게 두 세계를 잇는 지점
@MessageMapping("/game/withdraw")
public void withdraw(@Payload WithdrawRequest req, Principal principal) {
    morrisService.withdraw(new WithdrawCommand(actorId(principal), req.gameId()));
}
```

이 형태가 되면 P0-2는 **타입 시스템 수준에서 재발이 불가능해진다.**
`WithdrawCommand`를 만들려면 `actorId`가 필요한데, 그 값의 출처는 컨트롤러의 `Principal` 하나뿐이다.

### 부수 효과로 같이 해결되는 것들

| 문제 | 해결 |
| --- | --- |
| ❷ 서비스가 `SecurityContextHolder`를 뒤짐 | actor를 받으니 뒤질 이유가 없음. STOMP 스레드에서도 동작 |
| 서비스 단위 테스트에 Spring 필요 | Command는 순수 record → `new`로 끝 |
| `StonePlacementRequestDto`의 `finalPosition`이 1단계에선 미사용 | `PlaceCommand(actorId, gameId, to)` / `SlideCommand(actorId, gameId, from, to)` 로 분리 → 시그니처가 곧 문서 |
| 검증 위치가 불분명 | Command 컴팩트 생성자에서 형식 검증, 도메인에서 규칙 검증 |

### 비용과 한계

- 클래스가 는다. 이 프로젝트에서 필요한 Command는 8개 정도(`Create/Join/Leave/Start/Place/Slide/Remove/Withdraw/Draw`)라 감당 가능하다.
- **모든 서비스에 기계적으로 적용하지는 않는다.** 조회(Query)는 인자가 1~2개라 Command 없이
  원시 타입 파라미터로 충분하다. `getUserNickname(long userId)` 에 Command는 과하다.
- 규칙은 "**상태를 바꾸는 서비스 메서드**"에만 적용한다.

### 응답 방향도 같은 원칙

서비스는 응답 DTO가 아니라 **도메인 결과**를 반환한다.

```java
// 지금: 서비스가 StonePlacementResponseDto 를 직접 조립 (같은 빌더 코드가 6번 반복됨)
// 목표:
MoveResult result = morrisService.place(command);        // 도메인 결과
simpMessagingTemplate.convertAndSend(topic, GameStateResponse.from(result));  // 컨트롤러가 매핑
```

`MorrisService`에 `StonePlacementResponseDto.builder()...build()` 블록이
**거의 동일한 형태로 6번** 반복되는데(라인 88, 122, 145, 171, 200, 221, 257, 294, 331),
이건 서비스가 표현 계층 일을 하고 있다는 가장 뚜렷한 증거다.

---

## 3. 목표 의존 방향

```
┌──────────────────────────────────────────────┐
│ web        Controller, Request/Response DTO   │  Spring MVC / STOMP
└───────────────────┬──────────────────────────┘
                    │ Command / 원시 타입
┌───────────────────▼──────────────────────────┐
│ application  Service, Command, 이벤트 발행     │  Spring (트랜잭션만)
└───────────────────┬──────────────────────────┘
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
┌──────────────────┐   ┌────────────────────────┐
│ morris-core      │   │ persistence            │
│ 규칙 엔진 (순수)  │   │ Entity, Repository     │
│ Spring 의존 0    │   │ Spring Data JPA        │
└──────────────────┘   └────────────────────────┘
```

- `morris-core`는 **아무것도 의존하지 않는다.** 규칙 22개가 여기 산다([02](02-game-rules-audit.md)).
- `web`은 `persistence`를 모른다. 엔티티가 컨트롤러에 등장하지 않는다.
- 이 방향을 사람의 규율이 아니라 **모듈 경계와 ArchUnit 테스트로 강제**한다
  ([07](07-architecture-decision.md), [11](11-testing-strategy.md)).

---

## 4. 적용 순서

계층 정리를 한 번에 하면 리뷰가 불가능하다. 이 순서를 권한다.

1. **`morris-core` 추출** — 규칙 엔진만 순수 모듈로. 여기서 테스트가 처음 생긴다.
2. **actor 파라미터화** — `SecurityContextHolder` 직접 참조 6곳 제거. Command 도입.
   여기서 P0-2, P0-10이 함께 닫힌다.
3. **브로드캐스트 일원화** — `MorrisService`에서 `SimpMessagingTemplate` 제거.
4. **DTO 매핑 이동** — 서비스의 응답 DTO 조립 9곳을 컨트롤러/매퍼로.
5. **엔티티 의존 DTO 생성자 제거** — `GameRoomDto`, `UserRankDto`.
6. **ArchUnit 규칙 추가** — 되돌아가지 않도록 고정.
