# 13. 실행 로드맵

## 원칙

1. **안전망 → 규칙 → 보안 → 구조 → 인프라** 순서. 뒤집으면 뭘 깨뜨렸는지 알 수 없다.
2. **삭제·포맷·리네임은 기능 변경과 섞지 않는다.** 각각 단독 커밋.
3. 각 단계 끝에서 **애플리케이션이 뜨고 테스트가 초록**이어야 한다.
4. 멀티모듈·Redis·EKS 같은 큰 구조 변경은 **버그가 정리된 뒤에** 한다.

---

## Phase 0 — 다시 띄울 수 있게 만든다

> 지금 리포지토리를 클론하면 **빌드도 실행도 되지 않는다.** 여기부터다.

| # | 작업 | 참조 |
| --- | --- | --- |
| 0-1 | `application-example.yml` 작성 (필요 환경변수 8개 역추출) | [01](01-code-audit.md) P2-15 |
| 0-2 | 미사용 `@Value` 3개 제거 → 요구 환경변수 축소 | [04](04-consistency-and-naming.md) 6절 |
| 0-3 | 테스트 설정 정리 → `contextLoads()` 실제 통과 | [11](11-testing-strategy.md) 7절 |
| 0-4 | GitHub Actions CI 추가 (`./gradlew build`, `-x test` 없이) | [11](11-testing-strategy.md) 8절 |
| 0-5 | Dockerfile 멀티스테이지 + `eclipse-temurin` + `.dockerignore` | [12](12-dependency-upgrade.md) 8절 |
| 0-6 | docker-compose: `healthcheck`, 태그 고정, `platform` 제거 | [01](01-code-audit.md) P2-13 |
| 0-7 | README에 로컬 실행 방법 |  |

**완료 기준**: 깨끗한 클론에서 `docker compose up`으로 로컬 구동 + CI 초록.

---

## Phase 1 — 청소 (리뷰 부담 0)

> 전부 **삭제하거나 이름만 바꾸는** 작업. 동작이 바뀌지 않는다.

| # | 작업 | 참조 |
| --- | --- | --- |
| 1-1 | 데드코드 삭제 — `MorrisStatus` 본체, `LogoutService`, `socketUserMap`, 미사용 enum 6개, 주석 감싼 try/catch | [04](04-consistency-and-naming.md) 6절 |
| 1-2 | 미완성 자체 로그인 일체 삭제 (`MorrisUser` 외 5개 클래스) | [01](01-code-audit.md) P1-13 |
| 1-3 | thymeleaf 의존성 2개 + `static/index.html` 삭제 | [12](12-dependency-upgrade.md) 6절 |
| 1-4 | `.editorconfig` + Spotless 도입 후 포맷 일괄 적용 | [04](04-consistency-and-naming.md) 5-3 |
| 1-5 | 패키지 대문자 수정 (`dto/GameRoom` → `dto/gameroom`), `group = 'com'` 수정 | [04](04-consistency-and-naming.md) 5-3 |
| 1-6 | 로그 레벨 오용 정리 — sout 제거, 중복 ERROR 통합, 소켓 로그 `info`→`debug` | [10](10-logging-and-observability.md) 8절 1~4 |

**완료 기준**: 삭제된 코드 약 400줄, 동작 변화 없음.

---

## Phase 2 — `morris-core` 분리와 규칙 구현 ★ 가장 중요

> [02](02-game-rules-audit.md)의 규칙 22개 중 **11개가 미구현이거나 틀렸다.**
> 여기가 이 프로젝트의 실질적인 본체다.

| # | 작업 | 참조 |
| --- | --- | --- |
| 2-1 | 멀티모듈 전환 (`morris-core` / `morris-storage` / `morris-support` / `morris-api`) | [07](07-architecture-decision.md) ADR-1 |
| 2-2 | Java 21 toolchain (sealed interface + 패턴 매칭 switch 사용) | [12](12-dependency-upgrade.md) 4절 |
| 2-3 | 보드 표기법 테스트 픽스처 (`Board.parse`) | [11](11-testing-strategy.md) 4절 |
| 2-4 | **현재 맞는 것 먼저 테스트로 고정** — 밀 16개, 인접 24개, 종료 판정 | [11](11-testing-strategy.md) 9절 4 |
| 2-5 | 착수 검증 구현 — 턴 / 범위 / 소유권 / 빈 칸 / **인접 이동** | [02](02-game-rules-audit.md) B, D |
| 2-6 | **플라잉(3단계) 구현** — D-1과 반드시 함께 | [02](02-game-rules-audit.md) E-1 |
| 2-7 | **"상대 돌이 전부 밀이면 제거 가능" 예외** — 게임이 멈추는 버그 | [02](02-game-rules-audit.md) C-3 |
| 2-8 | 제거 권한 상태화 (`isRemoving`를 서버가 기억) | [02](02-game-rules-audit.md) C-5, [01](01-code-audit.md) P0-5 |
| 2-9 | 종료 판정 단일화 + 1단계 오적용 제거 | [02](02-game-rules-audit.md) F-2, F-3 |
| 2-10 | 무승부 — 합의 상태화 + 3회 반복 + 50수 규칙 | [02](02-game-rules-audit.md) G |
| 2-11 | 선공 랜덤/선택 (`RANDOM` 기본) — "방장"과 "흑돌" 분리 | [02](02-game-rules-audit.md) H-1 |
| 2-12 | **점수 부호 뒤집힘 수정** | [01](01-code-audit.md) P0-1 |
| 2-13 | 상태를 `RoomRegistry` 하나로 통합 (`ConcurrentHashMap` + 방 단위 락 + 정리) | [06](06-persistence-and-queries.md) 2절, [01](01-code-audit.md) P0-8 |

**완료 기준**: [11](11-testing-strategy.md) 4절 표의 24개 항목이 전부 초록.

---

## Phase 3 — 보안 · 서버 권위

> Phase 2에서 규칙이 서버에 생겼으니, 이제 **클라이언트를 믿지 않게** 만든다.

| # | 작업 | 참조 |
| --- | --- | --- |
| 3-1 | 토큰을 쿠키 → `Authorization` 헤더로 전환 | [07](07-architecture-decision.md) ADR-5 |
| 3-2 | STOMP `CONNECT` 인증 `ChannelInterceptor` | [08](08-guest-mode-design.md) 8절 |
| 3-3 | **클라이언트의 `/topic` 직접 발행 차단** | [01](01-code-audit.md) P0-12 |
| 3-4 | 서비스에서 `SecurityContextHolder` 직접 참조 제거 → actor 파라미터화 | [03](03-layering-and-dto.md) ❷ |
| 3-5 | Command 객체 도입 (`actorId` 필수) → **P0-2 기권 조작 구조적 차단** | [03](03-layering-and-dto.md) 2절 |
| 3-6 | 권한을 `ROLE_` 접두사로 저장 → `hasRole` 복구 | [01](01-code-audit.md) P1-2 |
| 3-7 | 인가 규칙 전환 (`permitAll` → `denyAll` 기본), 401 반환 | [08](08-guest-mode-design.md) 7절 |
| 3-8 | CORS 오리진을 `application.yml`로, WebSocket 오리진 명시 | [01](01-code-audit.md) P1-9, P1-10 |
| 3-9 | `@Valid` 추가 + `@MessageExceptionHandler` + `traceId` 에러 응답 | [01](01-code-audit.md) P1-12, P2-6 |
| 3-10 | P0/P1 하나마다 회귀 테스트 | [11](11-testing-strategy.md) 5절 |

**완료 기준**: [01](01-code-audit.md)의 P0 13개 전부 닫힘.

---

## Phase 4 — 영속성 · 랭킹

| # | 작업 | 참조 |
| --- | --- | --- |
| 4-1 | `User` PK 교체 (`provider` / `providerId`) + `@Setter` 제거 + 정적 팩터리 | [04](04-consistency-and-naming.md) 3절, [06](06-persistence-and-queries.md) 5절 |
| 4-2 | `GameRoom` 테이블 삭제 (로비 인메모리화) | [06](06-persistence-and-queries.md) 1절 |
| 4-3 | `matches` 테이블 + `@ManyToOne(LAZY)` 2개 | [09](09-ranking-design.md) 3절 |
| 4-4 | Elo 레이팅 (`morris-core` 순수 함수) + 랭크전/일반전 분기 | [09](09-ranking-design.md) 2절 |
| 4-5 | `GameFinishedEvent` → `RankingListener` (한 트랜잭션) | [09](09-ranking-design.md) 4절 |
| 4-6 | 쿼리 최적화 — 프로젝션, 단일 UPDATE, 인덱스, `open-in-view: false` | [06](06-persistence-and-queries.md) 3·4·6절 |
| 4-7 | `@Transactional` 정책 통일 | [04](04-consistency-and-naming.md) 2절 |
| 4-8 | **확정된 최종 스키마를 `V1__init.sql`로** + Flyway + `ddl-auto: validate` | [06](06-persistence-and-queries.md) 5절 |
| 4-9 | Testcontainers(MySQL) 기반 리포지토리 테스트 | [11](11-testing-strategy.md) 5절 |

**완료 기준**: 테이블 2개(`users`, `matches`), Flyway V1 고정.

---

## Phase 5 — API 규약 정리 + 게스트 (프론트와 동시 작업)

> **프론트엔드를 같이 고쳐야 하는 유일한 단계다.** 앞 단계와 분리해 둔 이유다.

| # | 작업 | 참조 |
| --- | --- | --- |
| 5-1 | REST 엔드포인트 규약 적용 (`/api/v1`, 복수 명사, 동사 제거) | [05](05-api-and-protocol.md) 3절 |
| 5-2 | STOMP 목적지 규약 적용 (`/app/rooms/{roomId}/<행위>`, 토픽 1개) | [05](05-api-and-protocol.md) 3절 |
| 5-3 | 용어 통일 (`tie`→`draw`, `withdraw`→`resign`, `gameId`→`roomId`) | [04](04-consistency-and-naming.md) 5-2 |
| 5-4 | `POST /api/v1/auth/guests` + 닉네임 생성기 + 레이트 리밋 | [08](08-guest-mode-design.md) 3절 |
| 5-5 | 재접속 복구 (`SNAPSHOT`) + 연결 끊김 30초 유예 | [02](02-game-rules-audit.md) H, [09](09-ranking-design.md) 2절 |
| 5-6 | 프론트: 게스트 버튼, 헤더 토큰, `connectHeaders`, 랭크/일반전 배지 | [08](08-guest-mode-design.md) 9절 |
| 5-7 | 프론트: GitHub Pages 배포 (`base`, `basename`, `404.html`) | [07](07-architecture-decision.md) ADR-4 |
| 5-8 | 프론트: 규칙 중복 제거 — 서버가 거절하면 그대로 표시 | [01](01-code-audit.md) P0-3 |

**완료 기준**: GitHub Pages 프론트에서 게스트로 한 판 완주.

---

## Phase 6 — 배포

| # | 작업 | 참조 |
| --- | --- | --- |
| 6-1 | **AWS Budgets 알림 먼저 설정** ($50/$100/$150) | [07](07-architecture-decision.md) ADR-2 |
| 6-2 | EC2 t4g.small + Docker (앱 + MySQL + Redis), 스왑 2GB | [07](07-architecture-decision.md) ADR-3 |
| 6-3 | nginx 재작성 (`map` upgrade, `proxy_read_timeout`, `X-Forwarded-Proto`) | [07](07-architecture-decision.md) ADR-4 |
| 6-4 | CloudFront 배포 + 보안그룹을 CloudFront 프리픽스로 제한 | [07](07-architecture-decision.md) ADR-4 |
| 6-5 | 카카오 Redirect URI 재등록 | [07](07-architecture-decision.md) ADR-4 |
| 6-6 | Actuator + 헬스체크 연결, Docker 로그 로테이션 | [10](10-logging-and-observability.md) 6절 |
| 6-7 | 게임 도메인 INFO 로그 + MDC + JSON 로깅 | [10](10-logging-and-observability.md) 3·4·5절 |
| 6-8 | Grafana Cloud 무료 티어 연결 + 커스텀 메트릭 5개 | [10](10-logging-and-observability.md) 7절 |
| 6-9 | `mysqldump` 백업 cron | [07](07-architecture-decision.md) ADR-3 |

**완료 기준**: `https://<user>.github.io/<repo>/`에서 실제 플레이 가능.

---

## Phase 7 — Spring Boot 업그레이드

> **배포가 안정된 뒤에.** 테스트가 충분히 쌓인 상태여야 한다.

| # | 작업 | 참조 |
| --- | --- | --- |
| 7-1 | 3.2.4 → 3.5.x (deprecation 정리) | [12](12-dependency-upgrade.md) 3절 |
| 7-2 | 3.5.x → 4.1.1 (스타터 이름, 테스트 애너테이션) | [12](12-dependency-upgrade.md) 3절 |
| 7-3 | jjwt 처리 — Resource Server로 대체 또는 0.13.0 | [12](12-dependency-upgrade.md) 5절 |

---

## Phase 8 — Redis

| # | 작업 | 참조 |
| --- | --- | --- |
| 8-1 | 리프레시 토큰 / 로그아웃 무효화 (독립적, 위험 낮음) | [07](07-architecture-decision.md) ADR-6 ③ |
| 8-2 | 게스트 발급 레이트 리밋 | ADR-6 ④ |
| 8-3 | 랭킹 ZSET + **"내 등수"** (DB 폴백 유지) | ADR-6 ② |
| 8-4 | `RedisRoomRegistry` 구현 (EKS 대비) | ADR-6 ① |

---

## Phase 9 — EKS (나중에. ArgoCD GitOps)

> **인프라 작업은 뒤로 미룬다.** [07](07-architecture-decision.md) ADR-2의 "선정리 7가지"가
> 끝나기 전에는 시작하지 않는다.

### 리포지토리가 분리된다

쿠버네티스 매니페스트는 이 리포지토리가 아니라 **별도 리포지토리**에 두고
**ArgoCD가 그것을 바라보며 동기화**한다.

```
NineMensMorris_BackEnd          이 리포. 애플리케이션 코드 + 이미지 빌드
        │
        │  CI 가 이미지를 푸시 (ghcr.io/ldhbenecia/nine-mens-morris:<git-sha>)
        ▼
ldh-infra-lab-manifests         매니페스트. ArgoCD 가 여기를 감시
        │  https://github.com/ldhbenecia/ldh-infra-lab-manifests
        ▼
     EKS 클러스터
```

### 이 리포지토리가 준비해야 하는 것 (Phase 9 전에)

| # | 작업 | 비고 |
| --- | --- | --- |
| 9-1 | **컨테이너 이미지를 레지스트리에 푸시** — GHCR | 공개 리포는 무료. ECR을 쓰면 크레딧을 깎는다 |
| 9-2 | **이미지 태그를 git SHA로** | `latest`는 금지. **태그가 바뀌지 않으면 ArgoCD가 변경을 감지하지 못한다** |
| 9-3 | 멀티 아키텍처 빌드 (`linux/arm64`) | t4g 노드. [12](12-dependency-upgrade.md) 8절 |
| 9-4 | `SPRING_PROFILES_ACTIVE`를 환경변수로 (ENTRYPOINT 하드코딩 제거) | 같은 이미지를 로컬/EKS에서 재사용 |
| 9-5 | 설정 전부 외부화 → ConfigMap/Secret으로 주입 가능한 형태 | [01](01-code-audit.md) P2-15 |
| 9-6 | `server.shutdown=graceful` + `terminationGracePeriodSeconds` | 진행 중 게임 보호 |
| 9-7 | Actuator liveness/readiness 프로브 | [10](10-logging-and-observability.md) 6절 |

**9-2가 GitOps에서 가장 흔한 함정이다.** 이미지를 `:latest`로 밀면
매니페스트가 그대로라 ArgoCD가 "동기화됨" 상태를 유지하고 배포가 안 된다.
CI가 SHA 태그로 푸시하고, 매니페스트 리포의 태그를 갱신하거나
**ArgoCD Image Updater**가 자동으로 갱신하게 한다.

### 매니페스트 리포에 들어갈 것

| 항목 | 워크로드 | 비고 |
| --- | --- | --- |
| 앱 | Deployment (`replicas: 1`) + Service(NodePort) | `replicas: 2`는 상태 분리 후에 ([07](07-architecture-decision.md) ADR-6) |
| MySQL | StatefulSet + EBS CSI PVC | |
| Redis | StatefulSet + PVC | |
| Secret | **평문 커밋 금지** — Sealed Secrets / SOPS / External Secrets | GitOps에서 반드시 결정해야 할 항목 |
| 모니터링 | kube-prometheus-stack + Loki (Helm) | Mimir/Tempo 제외 ([10](10-logging-and-observability.md) 7절) |

### 클러스터 운영

| # | 작업 |
| --- | --- |
| 9-8 | EC2 내린다 (예산이 겹치면 안 된다) |
| 9-9 | `eksctl`로 클러스터 (퍼블릭 서브넷, **NAT·ALB 없이**, 최신 k8s 버전) |
| 9-10 | ArgoCD 설치 + `Application` CR로 매니페스트 리포 연결 |
| 9-11 | 실습 종료 후 **클러스터 삭제 + 잔여 리소스 확인** (EBS·EIP·ENI) |

---

## 우선순위 요약

**당장 급한 것부터 셋만 고른다면:**

| 순위 | 항목 | 이유 |
| --- | --- | --- |
| 1 | **[01](01-code-audit.md) P0-1 점수 부호 뒤집힘** | 한 줄 수정. 지금 진 사람이 점수를 얻는다 |
| 2 | **Phase 2 규칙 엔진** | 서버가 규칙을 모른다. 나머지 전부의 전제 |
| 3 | **Phase 3-5 Command 도입** | P0-2 기권 조작을 구조적으로 닫는다 |

**반대로 나중에 해도 되는 것:**

- 멀티모듈 (Phase 2에 묶여 있지만, 급한 건 규칙이지 모듈이 아니다)
- 네이밍 정리 (Phase 1·5에 분산)
- Kafka — [07](07-architecture-decision.md) ADR-7에서 **미도입 결정**
- Tempo / Mimir — [10](10-logging-and-observability.md) 7절에서 **제외 결정**

---

## 진행 상황

각 Phase 완료 시 여기에 날짜를 기록한다.

| Phase | 상태 | 완료일 |
| --- | --- | --- |
| 0. 다시 띄우기 | ☐ | |
| 1. 청소 | ☐ | |
| 2. 규칙 엔진 | ☐ | |
| 3. 보안 | ☐ | |
| 4. 영속성·랭킹 | ☐ | |
| 5. API·게스트 | ☐ | |
| 6. 배포 | ☐ | |
| 7. 업그레이드 | ☐ | |
| 8. Redis | ☐ | |
| 9. EKS | ☐ | |
