# 11. 테스트 전략

---

## 1. 현재 상태

```java
@SpringBootTest
class NineMensMorrisApplicationTests {
    @Test void contextLoads() {}
}
```

**테스트가 이것 하나다.** 그리고 이마저 실제로는 돌지 않는다.

- DB 연결과 `JWT_SECRET_KEY`, `ACCESS_TOKEN_EXPIRATION`, `DOMAIN`,
  `DEFAULT_PROFILE_IMAGE` 등 환경변수가 없으면 컨텍스트 로딩이 실패한다.
- 그래서 `Dockerfile`이 `./gradlew build -x test`로 **테스트를 건너뛴다.**
- CI도 없다 (`.github/`에 워크플로가 없다).

즉 **173개 커밋 동안 테스트가 한 번도 실행된 적이 없다.**
[02](02-game-rules-audit.md)에서 규칙 22개 중 11개가 틀렸는데 아무도 몰랐던 이유다.

---

## 2. 왜 지금은 테스트를 쓸 수 없나

쓰기 싫어서가 아니라 **구조적으로 못 쓴다.**

```java
@Service
public class MorrisService {
    private final UserService userService;                    // → DB
    private final GameRoomRepository gameRoomRepository;      // → DB
    private final SimpMessagingTemplate simpMessagingTemplate;// → STOMP 브로커
```

"흑이 밀을 만들면 백 돌 하나를 제거할 수 있다"는 **순수한 규칙**을 검증하려는데
DB와 메시지 브로커를 띄워야 한다. 게다가 상태가 인스턴스 필드 `HashMap` 11개라
테스트마다 초기화도 안 된다.

**[07](07-architecture-decision.md) ADR-1에서 `morris-core`를 분리하는 이유가 이것이다.**
구조 취향이 아니라 테스트를 가능하게 만드는 전제 조건이다.

---

## 3. 목표 — 테스트 피라미드

이 프로젝트에 맞는 비중이다. 커버리지 숫자를 목표로 삼지 않는다.

```
        ▲   E2E (STOMP 왕복)         ~5개    느림. 핵심 시나리오만
       ╱ ╲
      ╱   ╲ 통합 (@SpringBootTest)   ~15개   보안·영속성 경계
     ╱─────╲
    ╱       ╲ 슬라이스 (@DataJpaTest) ~10개   쿼리·제약
   ╱─────────╲
  ╱ 단위 (morris-core)  ────────────  ~80개   규칙 전부. Spring 없음. 밀리초
 ╱─────────────────────────────────╲
```

**80%가 `morris-core` 단위 테스트여야 한다.** 거기에 이 프로젝트의 복잡도가 전부 있다.

---

## 4. `morris-core` — 규칙 테스트

Spring 없이 `new`만으로 돌아간다. 수백 개를 돌려도 1초 안쪽이다.

### 보드 표기법을 먼저 만든다

테스트를 읽을 수 있게 만드는 게 먼저다. 24칸 배열을 손으로 채우면 아무도 못 읽는다.

```java
// 테스트 픽스처 — 문자열로 판을 그린다
Board board = Board.parse("""
    B--------B--------.
    |  .-----.-----.  |
    |  |  W--W--.  |  |
    B  .  .     .  .  .
    |  |  .--.--.  |  |
    |  .-----.-----.  |
    .--------.--------.
    """);
```

이렇게 해두면 [02](02-game-rules-audit.md)의 규칙 22개가 **읽히는 테스트**가 된다.

### 반드시 있어야 하는 테스트 — [02](02-game-rules-audit.md)와 1:1

| 규칙 | 테스트 | 현재 |
| --- | --- | --- |
| A-1 밀 16개 | 모든 밀 라인이 정확히 16개이고 좌표가 맞다 | ✅ 구현은 맞음 |
| A-2 인접 | 24개 지점의 인접 목록이 대칭이다 (`a∈adj(b) ⟺ b∈adj(a)`) | ✅ |
| B-1 턴 | 상대 턴에 두면 `NOT_YOUR_TURN` | ❌ **미구현** |
| B-2 빈 칸 | 돌이 있는 곳에 놓으면 `OCCUPIED` | ❌ |
| B-3 범위 | `-1`, `24`, `99` → `OUT_OF_BOARD` | ❌ |
| C-1 밀 형성 | 3개를 잇는 순간 제거 권한 부여 | ✅ |
| C-2 밀 보호 | 밀에 속한 상대 돌은 제거 불가 | ✅ |
| **C-3 전부 밀 예외** | **상대 돌이 전부 밀이면 밀 속 돌도 제거 가능** | ❌ **게임이 멈추는 버그** |
| C-4 소유권 | 내 돌을 제거하려 하면 거절 | ❌ |
| C-5 제거 권한 | 밀을 안 만들었는데 제거 시도 → `REMOVAL_NOT_PENDING` | ❌ |
| **D-1 인접 이동** | **2단계에서 인접하지 않은 칸으로 이동 → `NOT_ADJACENT`** | ❌ |
| D-2 소유권 | 상대 돌을 옮기려 하면 거절 | ❌ |
| D-3 출발지 | 빈 칸에서 출발 → `EMPTY_SOURCE` | ❌ |
| D-4 목적지 | 돌이 있는 칸으로 이동 → `OCCUPIED` | ❌ |
| **E-1 플라잉** | **3개 남으면 임의의 빈 칸으로 이동 가능** | ❌ **완전 미구현** |
| E-1b | 4개일 때는 플라잉 불가 (경계값) | ❌ |
| F-1 2개 패배 | 보드 위 돌이 2개가 되면 패배 | ⚠️ |
| F-2 이동 불가 | 2단계에서 움직일 수 없으면 패배 | ⚠️ |
| F-2b | **1단계에서는** 움직일 수 없어도 패배가 아니다 | ⚠️ |
| F-2c | **3개(플라잉)면 빈 칸이 하나라도 있으면 이동 가능** | ❌ |
| G-1 무승부 합의 | 요청하지 않았는데 수락 → 거절 | ❌ |
| G-1b | 자기가 요청하고 자기가 수락 → 거절 | ❌ |
| G-2 3회 반복 | 같은 국면 3회 → 무승부 | ❌ |
| G-3 50수 | 제거 없이 50수 → 무승부 | ❌ |

**`❌`가 붙은 항목이 곧 구현해야 할 목록**이다. 테스트를 먼저 쓰고 빨간 걸 초록으로 만든다.

### 속성 기반 테스트 (선택)

규칙이 많아 조합 폭발이 있으므로 불변식 몇 개를 걸어두면 값어치가 크다.

```java
// jqwik 등
@Property
void 어떤_수를_두어도_보드_위_돌_수는_카운터와_일치한다(@ForAll @Size(min=1,max=60) List<Move> moves) { ... }

@Property
void 거절된_수는_보드를_바꾸지_않는다(@ForAll Move move) { ... }
```

두 번째는 특히 중요하다. 지금 `placeStonePhaseTwo`는 **검증 실패 개념이 없어서
잘못된 입력이 보드를 오염시킨다**([01](01-code-audit.md) P0-3).

---

## 5. 통합 테스트

### 슬라이스 — `@DataJpaTest`

```java
@DataJpaTest
class UserRepositoryTest {
    @Test void 닉네임_중복이면_저장에_실패한다() { ... }        // 유니크 제약 검증
    @Test void 같은_카카오_계정을_두_번_저장할_수_없다() { ... }
    @Test void 레이팅_증감이_단일_UPDATE_로_처리된다() { ... }
    @Test void 게스트는_랭킹_조회에서_제외된다() { ... }
}
```

**MySQL로 돌려야 한다.** H2로 돌리면 방언 차이(`greatest`, `DATETIME(6)`, 유니크 제약 동작)로
운영에서만 터지는 문제가 생긴다.

```java
@Testcontainers
abstract class MySqlTestSupport {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
}
```

Testcontainers는 Docker가 필요하지만, 어차피 로컬에 Docker가 있고
CI(GitHub Actions)에서도 그대로 돌아간다.

### 보안 — `@WebMvcTest` + `spring-security-test`

지금 [01](01-code-audit.md)에 인가 관련 결함이 P1만 4개다. 테스트로 고정한다.

```java
@Test void 비인증_요청은_401을_받는다() { ... }                  // 현재 403 (P1-8)
@Test void 게스트는_랭킹_조회는_되지만_등재되지_않는다() { ... }
@Test void 다른_사용자의_id로_기권할_수_없다() { ... }            // P0-2
@Test void 방에_속하지_않은_사용자는_게임을_시작할_수_없다() { ... } // P0-9
```

**P0/P1 하나마다 테스트 하나**를 붙이는 게 목표다. 그래야 재발하지 않는다.

### ArchUnit — 계층 규칙 고정

[03](03-layering-and-dto.md)에서 정리한 의존 방향을 사람 규율이 아니라 테스트로 강제한다.

```java
@ArchTest static final ArchRule 서비스는_SecurityContextHolder_를_읽지_않는다 = ...;
@ArchTest static final ArchRule 서비스는_SimpMessagingTemplate_을_모른다 = ...;
@ArchTest static final ArchRule DTO_는_엔티티를_의존하지_않는다 = ...;
@ArchTest static final ArchRule 컨트롤러는_리포지토리를_직접_쓰지_않는다 = ...;
```

모듈을 4개로 안 나누고 2개로 가는 근거가 이것이다([07](07-architecture-decision.md) ADR-1).

---

## 6. E2E — STOMP 왕복

가장 비싸므로 **핵심 시나리오 5개**만 만든다.

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class GameE2eTest {
    @Test void 게스트_두_명이_방을_만들고_한_판을_끝낸다() { ... }
    @Test void 재접속하면_진행_중이던_판이_복구된다() { ... }
    @Test void 상대_턴에_둔_수는_거절되고_보드가_바뀌지_않는다() { ... }
    @Test void 클라이언트는_topic_으로_직접_발행할_수_없다() { ... }   // P0-12
    @Test void 게임_종료_시_레이팅이_한_트랜잭션으로_반영된다() { ... }
}
```

네 번째가 특히 중요하다. [01](01-code-audit.md) P0-12는 **서버 코드만 봐서는 발견되지 않는다.**
실제로 클라이언트를 붙여 `/topic`에 SEND를 시도해 봐야 확인된다.

---

## 7. 컨텍스트 로딩 문제 먼저 해결

지금 `contextLoads()`가 실패하는 이유는 **테스트용 설정이 없기 때문**이다.

```yaml
# src/test/resources/application.yml
spring:
  datasource: (Testcontainers 가 주입)
  jpa.hibernate.ddl-auto: validate
  flyway.enabled: true
JWT_SECRET_KEY: dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHktMzJieXRlcw==
ACCESS_TOKEN_EXPIRATION: 3600000
DOMAIN: http://localhost:3000
DEFAULT_PROFILE_IMAGE: http://localhost/default.png
```

그리고 [01](01-code-audit.md) P2-15에서 지적한 **미사용 `@Value` 3개**
(`REFRESH_TOKEN_EXPIRATION`, `ACCESS_TOKEN_HEADER`, `REFRESH_TOKEN_HEADER`)를
제거하면 테스트 설정도 그만큼 줄어든다. **안 쓰는 설정을 요구하지 않게 하는 것**도 테스트 용이성이다.

---

## 8. CI

`.github/workflows/`가 비어 있다. 최소 구성부터.

```yaml
name: build
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: ./gradlew build          # -x test 를 붙이지 않는다
```

**`Dockerfile`의 `./gradlew build -x test`도 걷어낸다.**
테스트를 건너뛰는 빌드를 배포 경로에 두면 테스트를 쓸 이유가 사라진다.

---

## 9. 작업 순서

```
1. 테스트 설정 정리 → contextLoads() 가 실제로 통과하게    (7절)
2. CI 추가 → 이 시점부터 초록/빨강이 보인다               (8절)
3. morris-core 분리                                    ([07](07-architecture-decision.md) ADR-1)
4. 4절 표의 ✅ 항목부터 테스트 작성 → 현재 동작을 고정
5. 4절 표의 ❌ 항목: 테스트 먼저 작성(빨강) → 구현(초록)
6. ArchUnit 규칙 추가                                   (5절)
7. P0/P1 하나마다 회귀 테스트 추가
8. E2E 5개                                             (6절)
```

**4번이 중요하다.** 지금 맞게 동작하는 것(밀 검출, 인접 테이블, 종료 판정)을 먼저 고정해야
5번에서 리팩터링할 때 뭘 깨뜨렸는지 알 수 있다.
