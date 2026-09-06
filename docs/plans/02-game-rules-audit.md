# 02. 나인멘스모리스 규칙 대조표

공식 규칙(World Draughts Federation / 통용되는 표준 룰) 기준으로 현재 구현을 하나씩 대조했다.

## 요약

| 구분 | 개수 |
| --- | --- |
| ✅ 올바르게 구현됨 | 4 |
| ⚠️ 부분 구현 / 조건 틀림 | 4 |
| ❌ 미구현 (서버가 검증 안 함) | 7 |
| ➕ 아예 없는 규칙 | 4 |

**핵심**: 판정 로직(밀 검출, 인접, 종료 조건)의 *데이터*는 정확하다.
문제는 **어떤 수가 합법인지 판단하는 코드가 존재하지 않는다**는 것이다.
서버는 클라이언트가 시킨 대로 보드 배열을 고칠 뿐이다.

---

## A. 보드와 초기 상태

### ✅ A-1. 24개 지점, 16개 밀 라인 — 정확함
`game/service/MorrisService.java:407-415`

`rowTriples` 8개 + `columnTriples` 8개 = 16개 밀. 전부 검증했고 오류 없다.
인덱스 배치는 7×7 격자 기준으로 다음과 같다.

```
0 --------- 1 --------- 2        (row 0)
|   3 ----- 4 ----- 5   |        (row 1)
|   |   6 - 7 - 8   |   |        (row 2)
9  10  11      12  13  14        (row 3)
|   |  15 -16 -17   |   |        (row 4)
|  18 ---- 19 ---- 20   |        (row 5)
21 -------- 22 -------- 23       (row 6)
```

### ✅ A-2. 인접 관계 24개 지점 전부 — 정확함
`game/service/MorrisService.java:512-521`

24개 항목을 전부 대조했고 오류 없다.
**다만 이 테이블은 `checkPlayerCanMove()` 메서드 *안에* 지역 변수로 선언되어 있어
호출할 때마다 24×n 크기의 2차원 배열이 새로 할당되고, 정작 착수 검증에는 쓰이지 않는다.**
`static final`로 빼고 이동 합법성 검사에 재사용해야 한다.

### ✅ A-3. 각 플레이어 9개 돌
`game/service/MorrisService.java:82-85`

### ✅ A-4. 밀 2개를 동시에 완성해도 제거는 1개
`checkRemovalConditions`가 `boolean`을 반환하므로 자연히 1회. 우연히 맞았지만 결과는 옳다.

---

## B. 1단계 — 배치 (Placing)

### ❌ B-1. 자기 차례에만 둘 수 있다 — 검증 없음
서버는 `currentTurns`를 관리하면서도 요청자와 대조하지 않는다.
애초에 `StonePlacementRequestDto`에 요청자 식별자가 없고, `Principal`도 안 본다.
→ 상대 턴에 대신 둘 수 있다.

### ❌ B-2. 빈 지점에만 놓을 수 있다 — 검증 없음
`game/service/MorrisService.java:367-370`

```java
private void placeStonePhaseOne(Long gameId, int initialPosition, String currentPlayerStone) {
    board[initialPosition] = currentPlayerStone;   // 무조건 덮어쓴다
}
```

상대 돌 위에 그대로 덮어쓸 수 있다. 상대 돌은 사라지지만 `guestTotal` 카운트는 그대로 →
보드와 카운터가 어긋난다.

### ❌ B-3. 좌표 범위 검증 없음
`initialPosition`이 `-1`이나 `100`이면 `ArrayIndexOutOfBoundsException`.
STOMP 핸들러에는 예외 처리가 없으므로(01의 P2-6) 클라이언트는 아무 응답도 못 받고 멈춘다.

### ⚠️ B-4. 배치 단계 종료 판정 — 로직은 맞으나 위치가 어색
`game/service/MorrisService.java:385-401`

`hostAddableStones`와 `guestAddableStones`가 모두 0이 되면 phase 2로 넘어간다. 결과는 옳다.
다만 `decreaseAddableStones()`라는 "차감" 이름의 메서드가 **단계 전환이라는 부수효과**를 갖는다.

---

## C. 밀(Mill)과 돌 제거

### ✅ C-1. 밀을 만들면 상대 돌 1개 제거
`checkRemovalConditions` — 방금 놓은/옮긴 좌표가 포함된 밀만 검사한다. 논리적으로 정확하다.

### ✅ C-2. 상대 밀에 속한 돌은 제거할 수 없다
`checkRowOrColumnTriples` + `isAllOpponentStones` — 기본 규칙은 구현되어 있다.

### ❌ C-3. **"상대 돌이 전부 밀에 속하면 제거 가능" 예외가 없다** ← 실제 게임이 멈추는 버그
`game/service/MorrisService.java:440-469`

공식 규칙:
> 상대의 돌 중 밀에 속하지 않은 돌이 하나도 없을 때에 한해, 밀에 속한 돌도 제거할 수 있다.

현재 구현에는 이 예외가 없다. 상대 돌 6개가 밀 2개를 이루고 있는 상황(흔하다)에서
내가 밀을 완성하면 **어떤 돌도 제거할 수 없고, 서버는 턴도 넘기지 않는다 → 게임 영구 정지.**

`removePosition == 99`라는 매직 넘버(`MorrisService.java:197`)가 이 상황의 임시 탈출구로 보인다.
하지만 99는 "제거를 건너뛰고 턴을 넘긴다"이므로, 제거가 **의무**인 규칙을 위반한다.
게다가 밀을 만들지 않았어도 99를 보내 턴을 넘길 수 있다.

**필요한 구현**

```java
boolean canRemove(Board board, Stone opponent, int at) {
    if (board.stoneAt(at) != opponent) return false;
    if (!board.isInMill(at)) return true;
    return board.allStonesInMill(opponent);   // ← 이 예외가 빠져 있다
}
```

### ❌ C-4. 제거 대상이 상대 돌인지 확인하지 않는다
01의 P0-4 참고. 내 돌을 제거해도 상대 카운트가 줄어든다.

### ❌ C-5. 밀을 만든 직후인지 확인하지 않는다
`removeOpponentStone`은 언제든 호출 가능하다. "제거 권한"이라는 상태가 서버에 없다.
`isRemoving`은 **응답에만 담기는 값**이고 서버가 기억하지 않는다.

### ➕ C-6. 밀 즉시 재형성 규칙이 문서화되어 있지 않다
돌을 밀 밖으로 뺐다가 바로 되돌려 밀을 다시 만드는 것을 표준 룰은 **허용**한다.
현재 구현도 허용한다(결과적으로 맞음). 다만 이를 금지하는 하우스 룰도 흔하므로
"우리는 표준 룰을 따른다"고 명시해 두는 편이 좋다.

---

## D. 2단계 — 이동 (Moving)

### ❌ D-1. **인접 이동 제약이 없다** — 가장 큰 규칙 위반
`game/service/MorrisService.java:372-377`

2단계에서 돌은 **인접한 빈 지점으로만** 움직일 수 있다.
현재 구현은 `finalPosition`을 그대로 받아 보드 반대편으로도 옮긴다.
`adjacentIndexes` 테이블은 이미 존재하는데(A-2) 이동 검증에 쓰이지 않는다.

### ❌ D-2. 자기 돌만 움직일 수 있다 — 검증 없음
### ❌ D-3. 출발지에 돌이 있어야 한다 — 검증 없음
빈 칸을 출발지로 지정하면 `"EMPTY"` 문자열이 목적지로 복사되어 보드가 오염된다.
### ❌ D-4. 목적지는 비어 있어야 한다 — 검증 없음

---

## E. 3단계 — 플라잉 (Flying) — **완전 미구현**

### ➕ E-1. 돌이 3개 남으면 어디로든 이동할 수 있다
공식 규칙:
> 한 플레이어의 돌이 정확히 3개가 되면, 그 플레이어는 인접 제약 없이
> 임의의 빈 지점으로 돌을 옮길 수 있다(flying / hopping).

현재 코드에 `phase 3`이라는 개념 자체가 없다. `gamePhases`는 1과 2만 쓴다.

역설적으로 **D-1(인접 제약)이 구현되지 않아서 플라잉이 항상 켜져 있는 상태**다.
D-1을 고치는 순간 E-1도 함께 구현하지 않으면, 3개 남은 플레이어가
움직일 수 없어 즉시 패배하는 잘못된 판정이 나온다. **두 규칙은 반드시 같이 구현해야 한다.**

또한 종료 조건 판정(`checkPlayerCanMove`)도 플라잉을 고려해야 한다.
돌이 3개인 플레이어는 보드에 빈 칸이 하나라도 있으면 항상 움직일 수 있다.

---

## F. 승패 판정

### ⚠️ F-1. 돌이 2개 이하가 되면 패배 — 조건 계산이 부정확
`game/service/MorrisService.java:476-486`

`hostTotal`/`guestTotal`은 **손에 든 돌 + 보드 위의 돌**의 합이다(9에서 시작해 제거될 때만 감소).
따라서 "보드 위의 돌이 2개 이하"라는 규칙과 의미가 다르다.
2단계에서는 손에 든 돌이 0이라 결과가 우연히 일치하지만, 개념이 섞여 있다.
`stonesInHand`와 `stonesOnBoard`를 분리해야 한다.

### ⚠️ F-2. 움직일 수 없으면 패배 — 1단계에도 적용되어 있다
`removeOpponentStone`이 단계 무관하게 종료 판정을 호출한다(01의 P2-5).
1단계에는 손에 든 돌을 놓으면 되므로 이 규칙이 적용되면 안 된다.

### ⚠️ F-3. 판정 로직이 두 곳에 중복되어 있다
`checkEndGameConditions`(475-489)와 `determineWinner`(491-508)의 본문이 동일하다.
`Optional<Stone> winner()` 하나로 합쳐야 한다.

### ⚠️ F-4. 기권 — 규칙은 맞으나 인증이 없다
승자/패자 판정 자체는 옳다. 다만 누가 기권했는지를 클라이언트가 정한다(01의 P0-2).

---

## G. 무승부

### ⚠️ G-1. 합의 무승부 — 검증 없는 껍데기
`game/service/MorrisService.java:350-365`

```java
public MorrisResponse<Void> tieRequest(TieRequestDto requestDto) {
    return MorrisResponse.response(ResponseType.REQUEST_DRAW, MorrisResponseCode.GAME_TIE_REQUEST);
}
```

- 요청/수락/거절이 **상태로 남지 않는다.** 요청한 적 없어도 `tie-accept`를 보내면 게임이 끝난다.
- 자기가 요청하고 자기가 수락할 수 있다.
- 무승부 시 점수 변동이 없다(설계 의도인지 누락인지 불명 → [09](09-ranking-design.md)에서 결정 필요).

### ➕ G-2. 3회 동형 반복 무승부 — 없음
같은 국면이 3번 나오면 무승부. 위치 해시를 기록해야 한다.

### ➕ G-3. 50수 무진전 무승부 — 없음
양쪽 합쳐 50수(또는 하우스 룰로 20수) 동안 제거가 한 번도 없으면 무승부.
**이게 없으면 2단계에서 양쪽이 왕복만 하는 무한 게임이 성립한다.**
현재 턴 타이머도 없어서 실제로 방이 영구히 남는다.

---

## H. 규칙 외 누락 기능

| 기능 | 현재 | 비고 |
| --- | --- | --- |
| 재접속 후 판 복구 | ❌ | `ResponseType.SYNC_GAME`이 선언만 되어 있음 |
| 턴 제한 시간 | ❌ | 무한 대기 가능 |
| 기보(착수 기록) | ❌ | 분쟁 검증·리플레이 불가. [09](09-ranking-design.md)의 전적 기능과 연계 |
| 관전 | ❌ | `/topic/game/{id}` 구독만 하면 사실상 관전이 되지만 의도된 설계는 아님 |
| 선공 결정 | ⚠️ | 아래 H-1 참고 — **개선 확정 항목** |

### H-1. 선공이 방장으로 고정되어 있다 → 랜덤 또는 선택제로 변경 (확정)
`game/service/MorrisService.java:80, 86`

```java
playerStones.put(gameId, PLAYER_ONE_STONE);      // 방장이 항상 흑돌
currentTurns.put(gameId, gameRoom.getPlayerOneId());  // 방장이 항상 선공
```

나인멘스모리스는 선공이 유리한 게임이라 방장 고정은 공정하지 않다.

**제안: 방 생성 시점이 아니라 "게임 시작 시점"에 정한다.**

방 생성 시 옵션으로 박아두면 선공을 바꾸려고 방을 지웠다 다시 만들어야 한다.
두 사람이 이미 모여 있는 상태에서 바꿀 수 있어야 한다.

| 모드 | 동작 |
| --- | --- |
| `RANDOM` (기본) | 서버가 시작 시점에 난수로 결정 |
| `HOST_FIRST` | 방장 선공 (현재 동작) |
| `GUEST_FIRST` | 참가자 선공 |

**흐름**

목적지 이름은 [05](05-api-and-protocol.md)에서 확정한 규약을 따른다.

```
방 입장 (2인)
   │
   ├─ /app/rooms/{roomId}/settings   { firstMove: "RANDOM" }   ← 방장만, 시작 전 몇 번이든 변경 가능
   │     └─▶ /topic/rooms/{roomId}   { type: "SETTINGS_CHANGED", ... }  ← 상대 화면에도 즉시 반영
   │
   └─ /app/rooms/{roomId}/start                                ← 방장만
         └─▶ /topic/rooms/{roomId}   { type: "STARTED", state: { turn: ... } }
             서버가 mode에 따라 선공을 확정해서 내려준다
```

- 설정값은 DB가 아니라 **방의 인메모리 상태**로 들고 있으면 된다.
  `GameRoom` 테이블에 컬럼을 추가할 필요가 없다 ([06](06-persistence-and-queries.md)의 "로비는 DB에 두지 않는다" 참고).
- **난수는 반드시 서버가 뽑는다.** 클라이언트가 결과를 보내게 하면 조작 지점이 하나 더 생긴다.
  클라이언트는 "모드"만 고르고 "결과"는 못 정한다.
- 변경 권한은 방장에게만. 그렇지 않으면 시작 직전에 상대가 몰래 바꿀 수 있다.
- 재대국 기능을 넣는다면 **"직전 판의 후공이 다음 판 선공" (자동 교대)** 이 표준에 가깝다.
  이 경우 모드는 `ALTERNATE`가 하나 더 생긴다.

**주의**: 현재 코드는 `PLAYER_ONE_STONE = "BLACK"`을 방장에 하드코딩해서
`switchTurn`(379-383)이 `playerOneId == 흑돌`을 전제로 돈다.
선공을 바꾸려면 **"누가 방장인가"와 "누가 흑돌인가"를 분리**해야 한다.
[04-consistency-and-naming.md](04-consistency-and-naming.md)의 host/guest ↔ playerOne/playerTwo 용어 혼용 문제와 같은 뿌리다.

---

## 구현 방향

위 항목 전부를 지금의 `MorrisService`에 조건문으로 덧붙이면 감당이 안 된다.
**규칙을 `MorrisService` 밖으로 꺼내 Spring 의존이 없는 순수 자바 모듈로 만드는 것**이
[07-architecture-decision.md](07-architecture-decision.md)에서 멀티모듈을 권하는 실질적 이유다.

목표 인터페이스(초안):

```java
// morris-core — Spring 의존 0, 순수 함수
public final class MorrisGame {
    public MoveResult apply(Stone actor, Move move);
}

public sealed interface Move {
    record Place(int to) implements Move {}
    record Slide(int from, int to) implements Move {}
    record Remove(int at) implements Move {}
    record Resign() implements Move {}
    record OfferDraw() implements Move {}
    record RespondDraw(boolean accept) implements Move {}
}

public sealed interface MoveResult {
    record Applied(GameSnapshot state) implements MoveResult {}
    record MillFormed(GameSnapshot state) implements MoveResult {}   // 제거 권한 부여
    record Finished(GameSnapshot state, Outcome outcome) implements MoveResult {}
    record Rejected(Reason reason) implements MoveResult {}
}

public enum Reason {
    NOT_YOUR_TURN, OUT_OF_BOARD, OCCUPIED, EMPTY_SOURCE, NOT_YOUR_STONE,
    NOT_ADJACENT, REMOVAL_NOT_PENDING, PROTECTED_BY_MILL, GAME_ALREADY_FINISHED
}
```

이렇게 하면 위 규칙 22개가 전부 **Spring 컨텍스트 없이 단위 테스트 가능한 순수 함수**가 된다.
특히 C-3(전부 밀인 경우 예외), D-1(인접), E-1(플라잉)은 표로 만든 테스트 케이스로 검증하기 쉽다.
