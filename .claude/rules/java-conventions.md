# Java / Spring 명명 · 구조 컨벤션

## 용어집 — 코드 식별자는 이 표를 따른다

같은 개념을 코드마다 다르게 부르던 것을 통일한다. **새 코드는 예외 없이 이 표를 쓴다.**

| 개념 | 코드 식별자 | 사용자에게 보이는 문구 | 쓰지 말 것 |
| --- | --- | --- | --- |
| 같은 색 돌 3개가 이룬 줄 | `mill` | 3연속 | `triple`, `rowTriples`, `columnTriples` |
| 판 위의 자리 (0~23) | `point` | 지점 | `position`, `index`, `cell` |
| 돌 | `stone` | 돌 | `piece` |
| 인접 | `adjacent` / `Adjacency` | | `neighbor` 단독 |
| 1단계 (돌 놓기) | `PLACING` | 배치 | `phase 1` |
| 2단계 (인접 이동) | `MOVING` | 이동 | `phase 2` |
| 3단계 (자유 이동) | `FLYING` | 날기 | `phase 3` |
| 기권 | `resign` | 기권 | `withdraw` |
| 무승부 | `draw` | 무승부 | `tie` |
| 방 / 방 식별자 | `room` / `roomId` | 방 | `game`, `gameId` |
| 방장 / 참가자 | `host` / `guest` | 방장 / 참가자 | `playerOne` / `playerTwo` |

**"밀(mill)"은 사용자에게 노출하지 않는다.** 한국어 사용자에게 전달되지 않는 용어라
화면 문구·에러 메시지는 "3연속"을 쓴다. 코드 식별자만 `mill` 이다.

## 모듈 경계

```
morris-api ──▶ morris-core
     │    └──▶ morris-storage ──┐
     └───────────────────────────┴──▶ morris-support
```

- **`morris-core` 에는 Spring / JPA / STOMP 의존성을 넣지 않는다.** 게임 규칙만 산다
- `morris-storage` 는 엔티티 + 리포지토리. 웹 계층이 엔티티를 직접 만지지 않게 한다
- `auth` / `game` / `user` 는 당분간 `morris-api` 안의 패키지

## 주석

- **음슴체.** `~한다` / `~이다` 금지
- **문장 끝에 온점을 찍지 않는다**
- **문서 경로를 적지 않는다.** 문서는 옮겨지거나 사라지고, 그러면 주석이 거짓말이 된다.
  배경이 필요하면 주석 안에서 자체적으로 설명한다
- Javadoc(`/** */`, `<p>`, `<pre>`) 을 쓰지 않는다. 일반 주석으로 통일
- 무엇을 하는지가 아니라 **왜 그런지**를 적는다. 코드를 읽으면 아는 내용은 쓰지 않는다

```java
// 만료 토큰 접속은 정상 흐름. ERROR 로 남기면 로그 도배
log.debug("JWT 검증 실패: {}", exception.getMessage());
```

## 객체 생성

| 종류 | 방식 |
| --- | --- |
| 요청 / 응답 DTO, Command | `record` |
| 엔티티 | `@NoArgsConstructor(PROTECTED)` + 정적 팩터리 |
| 그 외 | 필드 5개 이상 + 선택 필드가 섞였을 때만 `@Builder` |

- 엔티티에 `@Setter` / `@AllArgsConstructor` / `@Builder` 를 붙이지 않는다
  (필드 순서가 바뀌면 조용히 깨진다)
- 상태 변경은 의도가 드러나는 메서드로: `gainScore(int)`, `changeNickname(String)`

## 트랜잭션

- 서비스 클래스에 `@Transactional(readOnly = true)`, **쓰기 메서드에만** `@Transactional`
- `org.springframework.transaction.annotation.Transactional` 을 쓴다 (jakarta 것 아님)

## JPA 연관관계

- **`@ManyToOne(fetch = FetchType.LAZY)` 만 사용한다**
- `@OneToMany` / `@ManyToMany` / `@OneToOne` 금지. 필요하면 리포지토리 쿼리로 푼다
- N+1 은 `@EntityGraph` 또는 DTO 프로젝션으로

## 계층

- **서비스는 `@RequestBody` / `@Payload` 로 바인딩되는 타입을 파라미터로 받지 않는다.**
  컨트롤러가 Command 로 변환한다
- **Command 의 첫 인자는 항상 서버가 확정한 `actorId`**
  (클라이언트가 보낸 `userId` 를 신뢰하면 안 된다)
- 서비스는 `SecurityContextHolder` 를 직접 읽지 않는다. actor 는 파라미터로 받는다
  (STOMP 스레드에서는 비어 있다)
- 서비스는 `SimpMessagingTemplate` 을 모른다. 결과를 반환하고 브로드캐스트는 컨트롤러가 한다

## 테스트

- **given / when / then 주석으로 구조를 드러낸다.** 해당 구간이 없으면 생략
- `@DisplayName` 으로 무엇을 검증하는지 한국어로
- 메서드명도 한국어 스네이크케이스 (`밀은_16개다`)
- 슬라이스/통합 테스트는 H2 가 아니라 **Testcontainers MySQL** (방언 차이로 운영에서만 터지는 문제 방지)

## 포맷

`./gradlew spotlessApply` 가 단일 출처다. 손으로 맞추지 않는다.
