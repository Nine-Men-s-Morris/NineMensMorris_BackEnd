# NineMensMorris 백엔드 재정비 계획

2024-04 ~ 2024-08 에 작성된 초기 Spring Boot 프로젝트를 **2026-09 기준으로 전수조사**한 결과와
개선 계획을 담은 디렉터리다.

---

## 한 줄 요약

> **게임 규칙 검증이 서버에 사실상 없다.**
> 클라이언트가 보낸 좌표를 그대로 신뢰하고, 턴·소유권·인접성·밀(mill) 규칙 어느 것도 확인하지 않는다.
> 승패 점수 가감은 **부호가 뒤집혀** 있고, 게임 상태는 동기화 없는 `HashMap` 11개에 들어 있다.
> 멀티모듈이나 네이밍보다 **규칙 엔진 분리 + 서버 권위(server-authoritative) 확보가 최우선**이다.

| 지표 | 값 |
| --- | --- |
| P0 (즉시 수정) | **13건** |
| P1 (재배포 전 수정) | 17건 |
| P2 (개선 권장) | 18건 |
| 규칙 위반·누락 | **22개 중 11개** |
| 테스트 | `contextLoads()` 1개 (그마저 실행 안 됨) |

---

## 전제 조건 (확정)

| 항목 | 값 |
| --- | --- |
| 서버 | AWS EC2 **t4g.small** 약 5개월 + **EKS 약 1개월** (겹치지 않게) |
| 예산 | 프리티어 **$200 크레딧 / 6개월 소진형** — 계산 결과 약 $196 |
| 프론트엔드 | GitHub Pages `https://<user>.github.io/<repo>/` (`../NineMensMorris_FrontEnd`) |
| HTTPS | **CloudFront** (`*.cloudfront.net`). 도메인 미구입, certbot 불필요 |
| DB | **MySQL 8 유지** + **Redis** (같은 인스턴스) |
| 인증 | 카카오 로그인 유지 + **비로그인(게스트) 플레이 추가** |
| 랭킹 | 유지 + 확장 (**Elo**로 재설계) |
| 메시징 | Kafka·RabbitMQ **미도입** (`ApplicationEventPublisher`로 충분) |

---

## 문서 목록

### 진단

| 문서 | 내용 |
| --- | --- |
| [01. 코드 전수조사](01-code-audit.md) | 결함 48건 — 심각도순, 파일·라인 명시 |
| [02. 게임 규칙 대조표](02-game-rules-audit.md) | 공식 규칙 22개 대조. **플라잉 미구현, 인접 이동 미검증, 밀 예외 누락** |

### 설계 원칙

| 문서 | 내용 |
| --- | --- |
| [03. 계층 독립도와 DTO 경계](03-layering-and-dto.md) | 역방향 의존 9곳. **요청 DTO를 서비스까지 넘기는 관행이 P0-2를 만들었다** |
| [04. 컨벤션·네이밍·데드코드](04-consistency-and-naming.md) | 객체 생성 4가지, `@Transactional` 제각각, 빈혈 엔티티, `@ManyToOne` 정책 |
| [05. API·STOMP 규약](05-api-and-protocol.md) | `createGame` / `tie-request` / `signup` — 명명 규칙 4가지 혼재 |

### 결정

| 문서 | 내용 |
| --- | --- |
| [06. 영속성·쿼리](06-persistence-and-queries.md) | 무엇을 DB에 둘 것인가 · 쿼리 최적화 · Flyway는 **최종 스키마를 V1으로** |
| [07. 아키텍처 결정 (ADR 7건)](07-architecture-decision.md) | 모듈 구조 · EKS 비용 · DB · CloudFront · 토큰 · Redis · Kafka |
| [08. 게스트 모드 설계](08-guest-mode-design.md) | PK 교체 · 랭크전/일반전 분리 · 인가 규칙 · STOMP 인증 |
| [09. 랭킹 재설계](09-ranking-design.md) | Elo · `matches` 원장 · Redis ZSET으로 "내 등수" |

### 실행

| 문서 | 내용 |
| --- | --- |
| [10. 로깅·관측성](10-logging-and-observability.md) | 로그 6줄 중 4줄 오용 · 레벨 정책 · MDC · LGTM 스택 |
| [11. 테스트 전략](11-testing-strategy.md) | 규칙 테스트 24개 목록 · Testcontainers · ArchUnit |
| [12. 의존성 업그레이드](12-dependency-upgrade.md) | Boot 3.2.4 → 4.1.1 · Java 21 · Dockerfile 재작성 |
| [13. 실행 로드맵](13-roadmap.md) | **Phase 0~9. 여기서 시작한다** |

---

## 어디서부터 볼까

- **당장 뭘 해야 하나** → [13. 로드맵](13-roadmap.md)의 "우선순위 요약"
- **뭐가 잘못됐나** → [01. 전수조사](01-code-audit.md)의 P0 섹션
- **게임 규칙이 어디까지 틀렸나** → [02. 규칙 대조표](02-game-rules-audit.md)
- **왜 이렇게 결정했나** → [07. ADR](07-architecture-decision.md)

가장 급한 셋:

1. **[P0-1](01-code-audit.md) 승패 점수 부호 뒤집힘** — 한 줄 수정. 지금 **진 사람이 점수를 얻는다**
2. **[Phase 2](13-roadmap.md) 규칙 엔진 분리** — 서버가 규칙을 모른다. 나머지 전부의 전제
3. **[Phase 3](13-roadmap.md) Command 도입** — 상대 id로 기권시켜 승률을 조작할 수 있다

---

## 조사 범위

- 백엔드 소스 45개 파일 (`src/main/java` 44 + `static/index.html`)
- **프론트엔드** `../NineMensMorris_FrontEnd` — API·STOMP 계층
  (서버만 봐서는 발견되지 않는 [P0-12](01-code-audit.md)를 여기서 찾았다)
- 빌드/인프라: `build.gradle`, `Dockerfile`, `docker-compose.yml`, `.platform/nginx.conf`
- 커밋 173개 (2024-04-16 ~ 2024-08-11), 브랜치 `develop` 단일

**확인하지 못한 것**: `application.yml` 계열이 전부 `.gitignore` 대상이라 리포지토리에 없다.
`ddl-auto`, `open-in-view`, 커넥션 풀, 카카오 OAuth 설정의 실제 값은 미확인이며
해당 항목은 문서에 "확인 불가"로 표기했다.
