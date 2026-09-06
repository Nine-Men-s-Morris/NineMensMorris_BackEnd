# 05. REST 엔드포인트 · STOMP 목적지 규약

현재 명명 규칙이 **한 프로젝트 안에 네 가지**가 섞여 있다.
`createGame`(camelCase 동사), `tie-request`(kebab-case), `signup`(붙여쓰기), `games`(복수 명사).

---

## 1. 현재 REST 엔드포인트

| 메서드 | 경로 | 반환 | 문제 |
| --- | --- | --- | --- |
| `POST` | `/api/signup` | `SignUpResponseDto` | `auth` 하위가 아님. 비밀번호 해시 반환([01](01-code-audit.md) P1-11) |
| `GET` | `/api/user` | `UserResponseDto` | "내 정보"인데 경로에 드러나지 않음. 단수 |
| `GET` | `/api/user/{userId}` | `UserNicknameResponseDto` | 위와 같은 경로 아래인데 **전혀 다른 리소스**(남의 닉네임) |
| `GET` | `/api/rank` | `List<UserRankDto>` | 목록인데 단수 |
| `GET` | `/api/games` | `List<GameRoomDto>` | 복수 — 유일하게 규칙에 맞음 |
| `POST` | `/api/createGame` | `CreateGameResponseDto` | **경로에 동사** |
| `POST` | `/api/joinGame/{roomId}` | `ResponseEntity<String>` | **경로에 동사** + 평문 영어 문자열 반환 |
| `POST` | `/api/leaveGame/{roomId}` | `ResponseEntity<String>` | **경로에 동사** + 평문. 삭제인데 `POST` |
| — | `/api/oauth2/kakao` | (리다이렉트) | 로그인 시작은 `/oauth2/authorization/kakao`로 `/api` 밖 — 비대칭 |
| `POST` | `/api/auth/logout` | (리다이렉트) | 여기만 `auth` 하위 |

### 그 외 문제

- **응답 포맷이 세 가지다.** `ResponseEntity<Dto>` / `ResponseEntity<String>`(평문) / STOMP는 `MorrisResponse<T>` 래핑.
  `"Successfully joined the game."` 같은 영어 평문을 클라이언트가 파싱할 수는 없다.
- **상태 코드**: `createGame`만 201. `leaveGame`은 204가 맞다.
- **API 버전이 없다.** 프론트가 GitHub Pages로 따로 배포되므로 서버와 배포 시점이 어긋난다.
  버전 접두사가 있어야 구버전 프론트를 깨지 않고 바꿀 수 있다.

---

## 2. 현재 STOMP 목적지

**클라이언트 → 서버** (`/app` 접두사)

| 목적지 | 문제 |
| --- | --- |
| `/app/joinGame/{roomId}` | 혼자만 `/game` 하위가 아님. camelCase 동사 |
| `/app/game/startGame` | camelCase |
| `/app/game/placeStone` | camelCase |
| `/app/game/removeOpponentStone` | camelCase. 이름이 김 |
| `/app/game/withdraw` | 보드게임 표준 용어는 `resign` |
| `/app/game/tie-request` | **kebab-case** — 위 4개와 규칙이 다름 |
| `/app/game/tie-accept` | 〃 |
| `/app/game/tie-reject` | 〃 |

**서버 → 클라이언트**

| 목적지 | 페이로드 | 문제 |
| --- | --- | --- |
| `/topic/gameRoom/{roomId}` | **한국어 평문 문자열** (`"3번 게임 방에 참가했습니다."`) | camelCase |
| `/topic/game/{gameId}` | `MorrisResponse<T>` JSON, 그런데 소켓 끊김만 평문 `"SOCKET_ERROR"` | 같은 방인데 토픽이 둘 |
| `/queue/**` | — | 브로커에 등록만 하고 **아무도 안 씀**. 개인 대상 채널이 없어서 에러를 보낼 곳이 없다 |

### 구조적 문제

1. **`roomId`와 `gameId`가 사실 같은 값이다.**
   `MorrisController.joinGame(roomId)` → `startGame(roomId)` → `gameRoomRepository.findById(gameId)`.
   이름만 둘이다. 클라이언트는 둘이 다른 것인지 확신할 수 없다.
2. **식별자 전달 방식이 섞여 있다.** `/app/joinGame/{roomId}`는 경로 변수,
   `/app/game/placeStone`은 body의 `gameId`. body에 있으면 조작 지점이 되고,
   목적지별 인가 검사를 일괄 적용할 수 없다.
3. **한 방에 대해 토픽을 둘 구독해야 한다.**
4. `tie` / `draw` / `withdraw` 용어가 코드 안에서 섞인다.
   `ResponseType.GAME_DRAW`, `REQUEST_DRAW`, `REJECT_DRAW` ↔ `MorrisResponseCode.GAME_TIE_REQUEST`, `tieRequest()`.

---

## 3. 확정 규약

### REST

> - 소문자 **kebab-case**, **복수형 명사** 리소스, 경로에 **동사 금지**
> - 행위는 HTTP 메서드 또는 하위 리소스로 표현
> - 내 것은 `/me`
> - 전 경로에 `/api/v1` 접두사
> - 성공 응답은 **DTO 또는 빈 본문**. 평문 문자열 금지
> - 실패 응답은 전부 동일한 에러 스키마

| 현재 | 확정 | 상태 코드 |
| --- | --- | --- |
| `POST /api/signup` | **삭제** (자체 로그인 제거, [04](04-consistency-and-naming.md) 6절) | — |
| — | `POST /api/v1/auth/guests` (신규) | `201` |
| `POST /api/auth/logout` | `POST /api/v1/auth/logout` | `204` |
| `GET /api/user` | `GET /api/v1/users/me` | `200` |
| `GET /api/user/{userId}` | `GET /api/v1/users/{userId}` | `200` |
| `GET /api/rank` | `GET /api/v1/rankings` | `200` |
| `GET /api/games` | `GET /api/v1/rooms` | `200` |
| `POST /api/createGame` | `POST /api/v1/rooms` + `Location` 헤더 | `201` |
| `POST /api/joinGame/{roomId}` | `POST /api/v1/rooms/{roomId}/players` | `201` |
| `POST /api/leaveGame/{roomId}` | `DELETE /api/v1/rooms/{roomId}/players/me` | `204` |
| — | `GET /api/v1/rooms/{roomId}` (재접속 복구, [02](02-game-rules-audit.md) H) | `200` |
| `/api/oauth2/kakao` | 유지 — Spring Security OAuth2 규약이라 예외 | — |

**`game`이 아니라 `room`으로 통일한다.** 지금 `GameRoom` 하나가 방이자 대국이고
`roomId == gameId`이므로 식별자 이름을 하나로 줄인다. 앞으로 코드에서 `gameId`는 쓰지 않는다.

### 에러 응답 스키마 (전 채널 공통)

```json
{
  "code": "ROOM_FULL",
  "message": "방이 가득 찼습니다.",
  "traceId": "8f3c1a90"
}
```

- `code`는 기계용(안정적), `message`는 사람용(바뀔 수 있음).
- `traceId`는 [10](10-logging-and-observability.md)의 MDC 값과 같다 —
  사용자가 화면의 값을 알려주면 로그를 바로 찾을 수 있다.
- 현재 `CustomErrorResponse`는 `status`/`name`/`message` 구조이고 `traceId`가 없다.

### STOMP

> - 목적지도 **소문자 kebab-case**, `/app/rooms/{roomId}/<행위>` 형태
> - `roomId`는 **항상 경로 변수**. 페이로드에 식별자를 넣지 않는다
> - 브로드캐스트는 **방당 토픽 하나**. 구분은 페이로드의 `type` 필드로
> - 개인 대상(에러 등)은 `/user/queue/**`

**클라이언트 → 서버**

| 현재 | 확정 |
| --- | --- |
| `/app/joinGame/{roomId}` | REST로 이동 (`POST /api/v1/rooms/{id}/players`) |
| — | `/app/rooms/{roomId}/settings` (선공 모드 변경, [02](02-game-rules-audit.md) H-1) |
| `/app/game/startGame` | `/app/rooms/{roomId}/start` |
| `/app/game/placeStone` | `/app/rooms/{roomId}/place` — 1단계 배치 |
| — | `/app/rooms/{roomId}/move` — 2·3단계 이동 (신규 분리) |
| `/app/game/removeOpponentStone` | `/app/rooms/{roomId}/remove` |
| `/app/game/withdraw` | `/app/rooms/{roomId}/resign` |
| `/app/game/tie-request` | `/app/rooms/{roomId}/draw-offer` |
| `/app/game/tie-accept` | `/app/rooms/{roomId}/draw-accept` |
| `/app/game/tie-reject` | `/app/rooms/{roomId}/draw-decline` |

`place`와 `move`를 나누는 이유: 지금은 `StonePlacementRequestDto`가
`initialPosition` / `finalPosition` 두 필드를 갖는데 1단계에서는 `finalPosition`이 안 쓰인다.
목적지를 나누면 **페이로드가 곧 문서가 된다**([03](03-layering-and-dto.md) 2절).

```jsonc
// POST /app/rooms/42/place
{ "to": 7 }

// POST /app/rooms/42/move
{ "from": 7, "to": 4 }

// POST /app/rooms/42/remove
{ "at": 12 }
```

`gameId`, `userId`가 페이로드에서 전부 사라진다.
`roomId`는 경로에서, `userId`는 `Principal`에서 온다 — [01](01-code-audit.md)의 P0-2가 구조적으로 닫힌다.

**서버 → 클라이언트**

| 현재 | 확정 |
| --- | --- |
| `/topic/gameRoom/{roomId}` | `/topic/rooms/{roomId}` 로 통합 |
| `/topic/game/{gameId}` | 〃 |
| — | `/user/queue/errors` — 요청자에게만 가는 거절/오류 |

```jsonc
// /topic/rooms/42
{
  "type": "STATE_CHANGED",
  "state": { "board": [...], "turn": 1234, "phase": "MOVING", ... }
}
```

**이벤트 타입 정리** — 현재 `ResponseType` 9개 중 접두사가 `GAME_`인 것과 아닌 것이 섞여 있고
2개는 미사용이다. 접두사를 전부 떼고 통일한다.

| 현재 | 확정 |
| --- | --- |
| `GAME_START` | `STARTED` |
| `GAME_STATE_UPDATE` | `STATE_CHANGED` |
| `GAME_OVER` | `FINISHED` |
| `GAME_WITHDRAW` | `FINISHED` (결과에 `RESIGN` 사유) |
| `GAME_DRAW` | `FINISHED` (결과에 `DRAW` 사유) |
| `REQUEST_DRAW` | `DRAW_OFFERED` |
| `REJECT_DRAW` | `DRAW_DECLINED` |
| `SYNC_GAME` (미사용) | `SNAPSHOT` — 재접속 복구에 실제로 사용 |
| `ERROR` (미사용) | `/user/queue/errors` 로 이동 |

**용어 통일**: `tie` → **`draw`**, `withdraw` → **`resign`**, `gameId` → **`roomId`**.
현재 `tie`와 `draw`가 같은 개념에 섞여 쓰인다([04](04-consistency-and-naming.md) 5-1).

### 소켓 엔드포인트 자체

```java
registry.addEndpoint("/morris-websocket").setAllowedOriginPatterns("*");
```

- `"*"` → 프론트 오리진 명시로 변경 ([01](01-code-audit.md) P1-9, CSWSH).
- 엔드포인트 경로도 규약에 맞춰 `/ws` 또는 `/api/v1/ws`로.
- `CONNECT` 프레임에서 인증하는 `ChannelInterceptor` 추가
  ([01](01-code-audit.md) P1-14 — 게스트 도입 시 필수).

---

## 4. 호환성

프론트엔드가 별도 리포지토리이므로 **엔드포인트를 바꾸면 프론트도 같이 고쳐야 한다.**
서버를 새로 띄우는 시점이라 사용 중인 클라이언트가 없으므로 **한 번에 바꾸는 편이 낫다.**
단계적 마이그레이션(구/신 병행 노출)은 이 규모에서 비용만 크다.

프론트 작업 순서와 맞추기 위해 **엔드포인트 확정 → 프론트에 공유 → 양쪽 동시 수정** 순으로 간다.
확정 시점에 이 문서를 프론트 리포지토리에도 링크해 두는 것이 좋다.
