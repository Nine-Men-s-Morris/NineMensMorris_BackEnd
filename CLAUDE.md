# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## 5. Verify Before Claiming

**Never report success or "no bugs" without evidence. Run the check.**

- Before saying "done": run the relevant build / typecheck / tests and state the result. If you couldn't run it, say so explicitly.
- "I found no bugs" requires an actual audit — read the code paths, trace concrete failure scenarios. A glance is not an audit. When asked to find bugs, assume there ARE some until you've genuinely looked, and report *what you checked* and *how* (file:line, scenario), not just a verdict.
- Report outcomes faithfully: if tests fail, show the output; if a step was skipped, say it. No silent success, no hedging when it actually passed.
- For outward-facing / costly / irreversible actions (publishing to Notion, sending, deleting, large backfills that spend Claude tokens), confirm scope first and don't claim it happened until verified from the actual result.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, clarifying questions come before implementation, and claims of "done/passing/no-bugs" are always backed by a run.

---

# 프로젝트 컨텍스트

나인멘스모리스(Nine Men's Morris) 온라인 대전 게임 백엔드.
2024년에 작성한 초기 Spring Boot 프로젝트를 2026년에 재정비하는 중이다.

## 스택

| | |
| --- | --- |
| 언어/런타임 | Java 21 (toolchain) |
| 프레임워크 | Spring Boot 3.5.x (→ 4.1.x 예정) |
| 실시간 | STOMP over WebSocket |
| 영속성 | Spring Data JPA + MySQL 8 |
| 인증 | 카카오 OAuth2 + 자체 JWT (jjwt) |
| 빌드 | Gradle (Wrapper 8.14.x), Spotless |
| 프론트엔드 | 별도 리포 `../NineMensMorris_FrontEnd` (React + Vite + @stomp/stompjs) |

## 명령어

```bash
./gradlew build              # 컴파일 + 테스트
./gradlew spotlessApply      # 포맷 적용 (커밋 전 필수)
./gradlew spotlessCheck      # 포맷 검사 (CI 에서 수행)
./gradlew bootRun            # 로컬 실행 (환경변수 필요)
docker compose up            # 앱 + MySQL
```

## 반드시 먼저 읽을 것

**`docs/plans/` 에 전수조사 결과와 확정된 설계 결정이 들어 있다.**
작업 전에 관련 문서를 확인하고, 결정과 어긋나는 구현을 하지 않는다.

| 문서 | 언제 보나 |
| --- | --- |
| [docs/plans/13-roadmap.md](docs/plans/13-roadmap.md) | **작업 시작 지점.** Phase 0~9 |
| [docs/plans/01-code-audit.md](docs/plans/01-code-audit.md) | 알려진 결함 (P0 13 / P1 17 / P2 18) |
| [docs/plans/02-game-rules-audit.md](docs/plans/02-game-rules-audit.md) | 게임 규칙 22개 대조표 |
| [docs/plans/07-architecture-decision.md](docs/plans/07-architecture-decision.md) | ADR 7건 (모듈·플랫폼·DB·HTTPS·토큰·Redis·메시징) |

## 이 코드베이스에서 특히 주의할 것

- **서버가 게임 규칙을 강제하지 않는다.** 턴·좌표·소유권·인접성 검증이 없다.
  규칙 관련 코드를 만질 때는 [02](docs/plans/02-game-rules-audit.md)의 대조표를 기준으로 한다.
- **클라이언트 페이로드를 신뢰하지 않는다.** 요청자 식별은 항상 `Principal`에서 가져온다.
  DTO에 `userId`를 받는 코드는 취약점이다 ([03](docs/plans/03-layering-and-dto.md) 2절).
- **게임 상태는 `MorrisService`의 `HashMap` 11개**에 있고 동기화도 정리도 없다.
  건드릴 때 [06](docs/plans/06-persistence-and-queries.md) 2절의 목표 구조를 참고한다.
- **테스트가 사실상 없다.** 로직을 바꾸면 테스트를 같이 쓴다 ([11](docs/plans/11-testing-strategy.md)).

## 컨벤션

### 주석

- **음슴체로 작성.** `~한다` / `~이다` 금지
- **문장 끝에 온점을 찍지 않는다**
- 무엇을 하는지가 아니라 **왜 그런지**를 적는다. 코드를 읽으면 아는 내용은 쓰지 않는다

```java
// 만료 토큰 접속은 정상 흐름. ERROR 로 남기면 로그 도배
log.debug("JWT 검증 실패: {}", exception.getMessage());
```

```java
// 나쁜 예 — 코드에 이미 있는 내용이고, 서술체이며, 온점이 있다.
// 서명 키를 생성한다.
```

### 그 외

- 커밋 메시지: Conventional Commits + 한국어 본문 (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`)
- 삭제·포맷·리네임은 기능 변경과 **같은 커밋에 섞지 않는다**
- 서비스 클래스: `@Transactional(readOnly = true)` + 쓰기 메서드에만 `@Transactional`
- 엔티티: `@Setter` 금지, 정적 팩터리 + 의도가 드러나는 메서드
- JPA 연관관계: **`@ManyToOne(fetch = LAZY)` 만 사용.** `@OneToMany`/`@ManyToMany` 금지
- 요청 DTO / 응답 DTO / Command 는 `record`
- 자세한 규칙은 [04](docs/plans/04-consistency-and-naming.md), [05](docs/plans/05-api-and-protocol.md)
