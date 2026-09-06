# 07. 아키텍처 결정

각 항목을 **선택지 → 장단점 → 판단 기준 → 결정 → 근거** 순으로 정리한다.

## 판단 기준 (전 항목 공통)

이 프로젝트의 실제 조건이다. 아래 결정은 전부 여기에서 나온다.

| 항목 | 값 |
| --- | --- |
| 코드 규모 | 약 2,500 LOC, 클래스 45개 |
| 인원 | 1명 |
| 트래픽 | 사실상 없음. 마케팅 계획 없음. 동시 게임 수 한 자릿수 |
| 운영 기간 | 약 6개월 |
| 예산 | AWS 신규 계정 프리티어 = **$200 크레딧 / 6개월 소진형** |
| 프론트엔드 | GitHub Pages (`https://<user>.github.io/<repo>/`) |
| 도메인 | `ninemensmorris.site` 만료. **신규 구입 안 함** → CloudFront 기본 주소 사용 |
| 목적 | 동작하는 서비스 + 학습·포트폴리오 |

**중요**: "확장성"은 판단 기준이 아니다.
사용자를 늘릴 계획이 없으므로 확장성을 근거로 한 선택은 전부 기각한다.

---

# ADR-1. 모듈 구조

## 선택지

### A. 단일 모듈 유지 (현행) — 패키지로만 정리

| 장점 | 단점 |
| --- | --- |
| 변경 비용 0 | **의존 방향을 강제할 수단이 없다** |
| 빌드가 단순 | 게임 규칙 테스트에 Spring 컨텍스트가 필요 |
| | 지금 [03](03-layering-and-dto.md)에서 본 역방향 의존이 언제든 재발 |

### B. 2모듈 — `morris-core` (순수) + `morris-app` (Spring)

```
nine-mens-morris/
├── settings.gradle
├── morris-core/          Spring·JPA·STOMP 의존 0. 규칙 엔진 전부
│   └── Board, Mills, Adjacency, MorrisGame, Move, MoveResult, Phase, Stone
└── morris-app/           implementation project(':morris-core')
    └── web / application / persistence / config
```

| 장점 | 단점 |
| --- | --- |
| **규칙 엔진이 Spring을 참조하는 것이 컴파일 단계에서 불가능해진다** | 모듈이 2개가 됨 (Gradle 설정 소폭 증가) |
| [02](02-game-rules-audit.md)의 규칙 22개를 `new MorrisGame()`만으로 테스트 가능 | `morris-app` 내부 계층은 여전히 규율에 의존 |
| 규칙 테스트가 밀리초 단위 — 수백 개를 돌려도 부담 없음 | **`morris-app`이 코드의 90%라 경계가 사실상 하나만 생긴다** |

### C. 4모듈 — `core` / `storage` / `support` / `api`

`life-tracker` 프로젝트에서 쓰는 형태다.

| 장점 | 단점 |
| --- | --- |
| 모듈마다 맡을 일이 분명하다 | 파일 이동 비용 (한 번뿐이지만 diff가 크다) |
| 웹 계층이 엔티티를 만질 수 없게 된다 | `api`가 여전히 가장 크다 (~30클래스) |
| B의 이점(규칙 격리)을 그대로 포함한다 | |

> **주의**: `domain`을 `storage`와 따로 두는 엄격한 변형은 채택하지 않는다.
> 엔티티 ↔ 도메인 객체 이중화와 매퍼가 필요해지는데, 저장소를 교체할 계획이 없고
> `@EntityGraph`·프로젝션을 쓰는 순간 경계가 새기 때문이다. **엔티티를 `storage`에 그대로 둔다.**

### D. 헥사고날 (port/adapter 모듈 분리)

C의 단점이 더 크게 나타난다. 이 규모에서는 검토 대상이 아니다.

### E. 도메인별 수직 분할 — `auth` / `game` / `user` / `db` / `support`

C가 **계층**으로 자른다면 E는 **기능**으로 자른다. 현재 의존 그래프를 실제로 뽑아봤다.

```
support(common) ◀── user ◀──┬── auth
                            ├── game
                            └── security ◀── config
```

**순환은 없다.** 도메인 분할이 구조적으로 불가능하진 않다. 문제는 다른 데 있다.

**`user`가 사실상 공유 커널이다.** 세 곳이 `UserRepository`를 직접 참조한다.

```
auth/service/CustomOAuth2UserService.java:5     import ...user.repository.UserRepository;
game/service/GameRoomService.java:11            import ...user.repository.UserRepository;
security/filter/JwtAuthenticationFilter.java:5  import ...user.repository.UserRepository;
```

| 장점 | 단점 |
| --- | --- |
| 기능 단위로 코드가 모인다 | `auth`·`game`을 진짜 독립 모듈로 만들려면 `user`가 리포지토리 대신 **좁은 인터페이스**만 노출해야 한다. **그 선행 작업이 모듈 분리보다 크다** |
| 실무에서 흔한 형태 | **테이블이 `users`/`matches` 2개**다. 도메인별로 나눌 데이터가 없다 |
| | 45클래스를 5~6모듈로 나누면 모듈당 7~8개 |

---

## 결정: **C 계열 4모듈**

멀티모듈을 지향한다는 전제에서, **모듈마다 실제로 맡을 일이 있는 선**으로 자른다.
B(2모듈)는 `morris-app`이 코드의 90%를 차지해 경계가 사실상 하나도 안 생기고,
E(도메인 7모듈)는 선행 작업이 본작업보다 크다.

```
nine-mens-morris/
├── morris-core/       게임 규칙 엔진.  Spring·JPA·STOMP 의존 0
│                      Board, Mills, Adjacency, MorrisGame, Move, MoveResult, Phase, Stone, Elo
├── morris-storage/    엔티티 + 리포지토리
│                      User, Match, UserRepository, MatchRepository, Flyway 마이그레이션
├── morris-support/    공통 응답 · 예외 · 에러코드 · MDC · 로깅 설정
└── morris-api/        web(REST·STOMP) + application(service) + security + 부트 진입점
                       내부 패키지: auth/ game/ user/
```

```
morris-api ──▶ morris-core
     │    └──▶ morris-storage ──┐
     └───────────────────────────┴──▶ morris-support
```

| 모듈 | Spring 의존 | 대략 클래스 수 |
| --- | --- | --- |
| `morris-core` | **없음** | ~15 |
| `morris-storage` | Data JPA만 | ~8 |
| `morris-support` | 최소 (web 애너테이션 정도) | ~6 |
| `morris-api` | 전부 | ~30 |

## 근거

1. **`morris-core`가 이 분할의 핵심이다.**
   [02](02-game-rules-audit.md)에서 규칙 22개 중 11개가 미구현이거나 틀렸다.
   고치려면 테스트가 필요한데, 지금 `MorrisService`가 `SimpMessagingTemplate`과
   `GameRoomRepository`를 물고 있어 테스트를 쓸 수 없다.
   **`morris-core/build.gradle`에 Spring이 없으면 그 유입이 컴파일 단계에서 막힌다.**
   구조 취향이 아니라 버그 수정의 전제 조건이다.

2. **`morris-storage`를 떼면 웹 계층이 엔티티를 만질 수 없다.**
   지금 `GameRoomDto`가 `GameRoom` 엔티티를 생성자로 받는 등
   [03](03-layering-and-dto.md) ❼의 역방향 의존이 있다.
   모듈이 갈리면 무엇을 노출할지 **의식적으로 결정**해야 한다.

3. **`auth` / `game` / `user`는 당분간 `morris-api` 안의 패키지로 둔다.**
   E에서 본 대로 이 셋을 모듈로 올리려면 `user`의 공유 커널부터 해체해야 한다.
   **패키지 배치는 지금과 같으므로, 나중에 승격할 때 디렉터리 이동 + `settings.gradle` 한 줄이면 된다.**
   먼저 `user`가 `UserRepository` 대신 좁은 인터페이스를 노출하도록 바꾸고, 그다음에 올린다.

4. **모듈이 잡아주지 못하는 경계는 ArchUnit으로 보완한다.**
   `morris-api` 안의 계층 규칙은 모듈로 표현되지 않는다.

   ```java
   @ArchTest static final ArchRule 서비스는_웹을_모른다 =
       noClasses().that().resideInAPackage("..application..")
           .should().dependOnClassesThat().resideInAnyPackage("..web..", "jakarta.servlet..");

   @ArchTest static final ArchRule 서비스는_시큐리티컨텍스트를_직접_읽지_않는다 =
       noClasses().that().resideInAPackage("..application..")
           .should().accessClassesThat().haveFullyQualifiedName(
               "org.springframework.security.core.context.SecurityContextHolder");
   ```

   두 번째는 [03](03-layering-and-dto.md) ❷를 그대로 고정한다.

5. **다만 구조 개편이 버그를 고쳐주지는 않는다.**
   4모듈로 나눠도 P0-1(점수 부호), P0-2(기권 조작), C-3(밀 예외)은 그대로 남는다.
   **모듈 재배치와 규칙 구현을 같은 단계([13](13-roadmap.md) Phase 2)에 묶어서**
   "옮기고 끝"이 되지 않게 한다.

## 실행

```groovy
// settings.gradle
rootProject.name = 'nine-mens-morris'
include 'morris-core', 'morris-storage', 'morris-support', 'morris-api'
```

```groovy
// morris-core/build.gradle — 의존성이 이것뿐이어야 한다
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.assertj:assertj-core'
}
```

**`morris-core/build.gradle`에 Spring 의존성이 한 줄이라도 추가되면 잘못 가고 있다는 신호다.**

`bootJar`는 `morris-api`에서만 만든다. 나머지 세 모듈은 `jar { enabled = true }`, `bootJar { enabled = false }`.

## 배치를 옮겨야 하는 것

| 대상 | 현재 | 이동 |
| --- | --- | --- |
| `CustomOAuth2User` | `user/domain/` | **`auth/`** — auth 개념인데 user 패키지에 있어서 `auth → user` 의존을 만든다 |
| `common/` | — | `morris-support` |
| `MorrisStatus.Status` | `game/domain/` 중첩 enum | `morris-core`의 최상위 `GameStatus` |
| `User`, `UserRepository` | `user/` | `morris-storage` |

---

# ADR-2. 실행 플랫폼 — EKS를 6개월 상시 운영할 수 있는가

목표: **Kubernetes / EKS 학습**. 제약: **$200 크레딧 / 6개월**.

## 비용 계산 (서울 리전 온디맨드, 대략)

프리티어가 2025-07-15부터 **"$100~200 크레딧 / 6개월 소진형"** 으로 바뀌었다.
모든 서비스가 **같은 크레딧을 함께 깎아먹는다.** 예전의 "750시간 무료"가 아니다.

| 항목 | 시간당 | 월 | 6개월 |
| --- | --- | --- | --- |
| **EKS 컨트롤 플레인 (클러스터 1개)** | **$0.10** | **~$73** | **~$438** |
| EC2 t4g.micro (2vCPU / 1GiB) | ~$0.0084 | ~$6.1 | ~$37 |
| EC2 t4g.small (2vCPU / 2GiB) | ~$0.0168 | ~$12.3 | ~$74 |
| EC2 t4g.medium (2vCPU / 4GiB) | ~$0.0336 | ~$25 | ~$147 |
| EBS gp3 20GB | — | ~$1.8 | ~$11 |
| Public IPv4 | $0.005 | ~$3.6 | ~$22 |
| ALB | ~$0.0225 + LCU | ~$18 | ~$108 |
| NAT Gateway | $0.059 | ~$43 | ~$258 |
| RDS db.t4g.micro + 20GB | ~$0.026 | ~$21 | ~$128 |

### EKS 상시 운영 시 최소 구성 견적

```
컨트롤 플레인                    $438
워커 노드 t4g.medium × 1         $147   ← 아래 "노드 크기" 참고
EBS 30GB                          $17
Public IPv4                       $22
────────────────────────────────────
                                 $624   (ALB·NAT 없이, 노드 1대)
```

**크레딧 $200의 3배가 넘는다.** ALB($108)나 NAT Gateway($258)를 추가하면 더 벌어진다.

### 노드 크기 — EKS는 오버헤드가 더 크다

워커 노드에는 애플리케이션 외에 **kubelet, kube-proxy, aws-node(VPC CNI), CoreDNS**가 돈다.
합쳐서 대략 **300~400MB**가 추가로 필요하다.
아래 ADR-3의 메모리 예산(앱+MySQL+Redis ≈ 1.1~1.3GB)에 이걸 더하면
**t4g.small(2GiB)로는 부족하고 t4g.medium(4GiB)이 필요하다.**

또 하나: VPC CNI는 노드의 ENI/IP 수만큼만 파드를 띄울 수 있다.
t4g.small은 **파드 11개**가 상한이라(시스템 파드 제외하면 여유가 거의 없다)
작은 인스턴스에서 EKS를 굴리면 메모리보다 IP 한도에 먼저 걸린다.

## 실제 계획

EKS는 인프라 공부 목적으로 **약 한 달 정도** 이 애플리케이션을 올려서 운영해 본다.
6개월 내내 EKS로 운영하지는 않는다. 나머지 기간은 EC2 단독.

## 결정: **EKS 1개월 + EC2 5개월. 두 기간이 겹치지 않게 한다**

### 예산 ($200 기준)

| 기간 | 구성 | 금액 |
| --- | --- | --- |
| EC2 단독 5개월 | t4g.small + EBS 20GB + IPv4 | ~$89 |
| EKS 1개월 | 컨트롤 플레인 $73 + t4g.medium 노드 $25 + EBS $5 + IPv4 $4 | ~$107 |
| | | **~$196** |

**$200에 거의 딱 맞는다. 여유가 없다.** 따라서 두 가지가 전제다.

1. **EKS를 쓰는 한 달 동안은 EC2를 내린다.** 병행하면 그 달에만 $130이 나가고 예산이 깨진다.
2. **NAT Gateway와 ALB를 쓰지 않는다.** 한 달만 써도 $43 + $18 = $61이 추가돼 초과한다.

Free 플랜은 크레딧이 떨어지면 청구되는 게 아니라 **리소스가 멈춘다.**
즉 위험은 요금 폭탄이 아니라 **게임 서버가 6개월을 못 채우고 죽는 것**이다.

### EKS를 띄울 때 비용 함정

| 함정 | 월 비용 | 회피 |
| --- | --- | --- |
| **NAT Gateway** | ~$43 | 노드를 **퍼블릭 서브넷**에 둔다. 학습용에 프라이빗+NAT는 과하다 |
| **ALB** (`type: LoadBalancer` / Ingress) | ~$18 | `NodePort` + 노드 퍼블릭 IP로 시작. ALB Controller 실습은 짧게 켰다 끄기 |
| 클러스터 삭제 후 잔여 리소스 | — | EBS 볼륨·Elastic IP·ENI가 남는다. `eksctl delete cluster` 후 콘솔 확인 |
| **Extended Support** | 6배 ($0.60/hr) | 오래된 k8s 버전은 자동 진입. **최신 버전으로 생성** |
| 비용 알림 미설정 | — | **AWS Budgets 알림($50/$100/$150)을 제일 먼저 건다** |

---

## EKS에 올리려면 애플리케이션 쪽에서 먼저 고쳐야 하는 것

이게 이 ADR의 실질적인 결론이다. **지금 코드는 EKS에 올릴 수 없다.**

| # | 문제 | 왜 EKS에서 문제인가 | 참조 |
| --- | --- | --- | --- |
| 1 | **Dockerfile이 깨져 있다** | 깨끗한 클론에서 빌드가 실패한다. `COPY build/libs/*.jar`가 호스트 컨텍스트를 본다 | [01](01-code-audit.md) P2-12 |
| 2 | **ARM 이미지가 아니다** | `platform: linux/amd64` 고정. t4g 노드에서 QEMU 에뮬레이션 | ADR-3 |
| 3 | **헬스체크 엔드포인트가 없다** | `livenessProbe` / `readinessProbe`를 걸 곳이 없다. Actuator 미포함 | [01](01-code-audit.md) P2-17 |
| 4 | **게임 상태가 파드 메모리에 있다** | 롤링 업데이트·노드 교체 때마다 **진행 중인 게임이 전부 사라진다.** 파드는 EC2보다 훨씬 자주 죽는다 | [06](06-persistence-and-queries.md) 2절 |
| 5 | **`replicas: 2` 이상이 불가능하다** | 인메모리 상태가 파드마다 갈라지고, STOMP 브로드캐스트가 다른 파드의 구독자에게 안 간다 | 아래 ADR-6 |
| 6 | **설정이 전부 하드코딩/환경변수 난립** | ConfigMap/Secret으로 분리해야 한다. 지금은 예시 파일조차 없다 | [01](01-code-audit.md) P2-15 |
| 7 | **Graceful shutdown이 없다** | `SIGTERM`을 받으면 진행 중 게임이 즉시 끊긴다. `server.shutdown=graceful` 필요 | — |

**4번과 5번이 [Redis 도입(ADR-6)](#adr-6-redis를-어디에-쓸-것인가)의 실질적 정당화다.**
EC2 단독 운영에서는 인메모리로도 충분하지만,
EKS에 올리는 순간 "파드는 언제든 죽는다"가 전제가 되므로 상태를 밖으로 빼야 의미가 생긴다.

**따라서 순서는 이렇다.**

```
EC2로 먼저 정상 운영  →  위 1~7 정리  →  그다음 EKS 한 달
```

EKS를 먼저 하면 애플리케이션 결함과 쿠버네티스 학습이 뒤엉켜서 둘 다 안 된다.

---

# ADR-3. 데이터베이스

전제: **직접 관리한다** (관리형 서비스·외부 BaaS 사용 안 함).
**RDS는 6개월 $128로 크레딧의 2/3를 먹으므로 검토에서 제외한다.**

## 추가 요구사항

- **MySQL을 유지한다** (DB 종류를 바꾸지 않는다)
- **Redis도 같은 인스턴스에 올린다** — 연습 목적 포함

이 두 가지가 정해지면 남는 질문은 하나다. **인스턴스 크기를 얼마로 할 것인가.**

## 메모리 예산

| 프로세스 | 실측 RSS (대략) | 비고 |
| --- | --- | --- |
| JVM (Spring Boot 3, `-Xmx512m`) | 600~700MB | 힙 외에 메타스페이스·스레드 스택·네이티브 포함 |
| MySQL 8 (기본 설정) | 350~450MB | `performance_schema`가 기본 ON |
| MySQL 8 (튜닝 후) | 250~300MB | 아래 설정 적용 시 |
| Redis (`maxmemory 64mb`) | 30~80MB | 이 워크로드에서는 실제 사용량이 훨씬 작다 |
| OS + Docker 데몬 | 150~200MB | |
| **합계 (튜닝 기준)** | **약 1.1~1.3GB** | |

**1GiB(t4g.micro)로는 불가능하다.** 스왑을 붙여도 MySQL이 스왑을 타면 성능이 무너진다.

## 선택지

| 옵션 | 6개월 비용 | 판정 |
| --- | --- | --- |
| **A.** t4g.micro (1GiB) + MySQL + Redis | ~$70 | ❌ **OOM.** 예산 초과 |
| **B.** t4g.small (2GiB) + MySQL + Redis | ~$107 | ✅ **채택** |
| **C.** t4g.medium (4GiB) | ~$181 | ❌ 크레딧 대부분 소진. 과함 |
| **D.** EC2 + RDS | ~$200+ | ❌ 크레딧 소진 |

## 결정: **B — t4g.small 한 대에 애플리케이션 + MySQL + Redis**

6개월 총액 약 $107 (EC2 $74 + EBS $11 + IPv4 $22).
**$200 크레딧의 약 절반**이 남으므로 여유가 있다.

## 근거

1. **DB 종류를 안 바꾸는 대가가 인스턴스 한 단계다.**
   PostgreSQL로 바꾸면 t4g.micro(2GiB 아님)에서도 여유가 있어 6개월에 $37을 아낄 수 있다.
   다만 그 $37을 아끼려고 **DB 마이그레이션 + 운영 방식 재학습**을 하는 건
   6개월짜리 개인 프로젝트에서 남는 장사가 아니다. MySQL을 그대로 간다.
   (참고: 네이티브 SQL이 한 줄도 없고 JPQL도 하나뿐이라 전환 자체는 쉽다.
   나중에 옮기고 싶어지면 그때 해도 비용이 거의 같다.)

2. **1GiB는 튜닝으로도 못 맞춘다.**
   MySQL을 250MB까지 조여도 JVM 600 + MySQL 250 + Redis 50 + OS 150 = 1,050MB.
   여유가 음수다. t4g.small은 **선택이 아니라 최소 요구사항**이다.

3. **크레딧 안에 들어온다.** 절반이 남으므로 S3 백업, CloudWatch 로그 같은
   부가 서비스를 조금 써도 6개월을 버틴다.

## 운영 체크리스트

```yaml
# docker-compose.yml — 현재 파일에서 고쳐야 할 것들
services:
  app:
    depends_on:
      db:    { condition: service_healthy }   # 현재 없음 → 기동 순서 어긋남
      redis: { condition: service_started }
    environment:
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=55"
    deploy:
      resources: { limits: { memory: 900M } }

  db:
    image: mysql:8.4                          # latest 금지 — 재현 가능한 태그로
    command: >
      --innodb-buffer-pool-size=192M
      --performance-schema=OFF
      --innodb-log-buffer-size=8M
      --max-connections=20
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      retries: 5
    deploy:
      resources: { limits: { memory: 400M } }

  redis:
    image: redis:7-alpine
    command: >
      redis-server --maxmemory 64mb
                   --maxmemory-policy noeviction
                   --appendonly yes
    deploy:
      resources: { limits: { memory: 100M } }
```

- `--maxmemory-policy`는 **`noeviction`** 으로. 랭킹·토큰 데이터를 담을 것이므로
  기본값(`noeviction`)을 유지하고 캐시 용도가 아님을 명시한다.
  `allkeys-lru`로 두면 랭킹이 조용히 사라진다.
- `--appendonly yes` — 재시작 시 랭킹·리프레시 토큰이 날아가지 않게.
- **스왑 2GB 설정** — 2GiB여도 안전망은 필요하다.
- 백업: `mysqldump` cron → 파일, 주기적 EBS 스냅샷. 테이블 2개라 덤프가 수 MB다.
- Docker 로그 로테이션(`max-size`, `max-file`) — 안 하면 EBS를 채운다.
- **아키텍처 주의**: t4g는 ARM(Graviton)이다.
  현재 `docker-compose.yml`의 `platform: linux/amd64`를 **지워야 한다.**
  안 지우면 QEMU 에뮬레이션으로 돌아 매우 느려진다.
  `mysql:8.4`, `redis:7-alpine`, `eclipse-temurin` 전부 arm64 이미지를 제공한다.
- Redis는 **외부에 포트를 열지 않는다** (`ports:` 지정 금지, Docker 네트워크 내부만).
  인증 없는 Redis가 인터넷에 노출되면 즉시 스캔에 걸린다.

---

# ADR-4. 배포 토폴로지와 HTTPS

## 문제

```
프론트엔드  https://<user>.github.io      ← GitHub Pages는 HTTPS 강제
백엔드      http://<EC2 퍼블릭 IP>:8080   ← 도메인 없음, 인증서 없음
```

**HTTPS 페이지에서 `http://` API를 호출하면 브라우저가 차단한다(mixed content).**
`ws://` 역시 마찬가지다. 즉 **API에 HTTPS가 없으면 서비스가 아예 동작하지 않는다.**

그런데 Let's Encrypt는 **IP 주소에 인증서를 발급하지 않는다.** 도메인이 필요하다.
기존 `ninemensmorris.site`는 만료되어 쓸 수 없다.

## 왜 이게 문제인가 — HTTPS는 선택이 아니다

```
프론트  https://<user>.github.io/<repo>/    ← GitHub Pages는 HTTPS로만 서비스된다
API     http://<EC2 퍼블릭 IP>:8080          ← 지금 상태
```

**HTTPS 페이지에서 `http://` 요청이나 `ws://` 소켓을 열면 브라우저가 차단한다**(mixed content).
`fetch`도 STOMP 연결도 전부 막힌다. 콘솔에 에러만 뜨고 요청이 나가지 않는다.
**우회 방법이 없다.** 프론트를 GitHub Pages에 올리는 이상 API도 HTTPS여야 한다.

그리고 HTTPS에는 인증서가 필요한데, **Let's Encrypt는 IP 주소에 발급하지 않는다.**
즉 어떤 형태로든 **호스트 이름**이 있어야 한다. 도메인을 사지 않는다면 이름을 어디서 얻을 것인가.

## 선택지

| 옵션 | 이름 | 추가 비용 | 판정 |
| --- | --- | --- | --- |
| **A.** CloudFront를 EC2 앞에 | `dxxxxx.cloudfront.net` (AWS가 줌) | 사실상 $0 | ✅ **채택** |
| **B.** 무료 서브도메인(DuckDNS 등) + certbot | `xxx.duckdns.org` | $0 | ❌ 사용 안 함 |
| **C.** 도메인 구입 + certbot | 직접 소유 | 연 $1~15 | ❌ 구입 안 함 |
| **D.** ALB + ACM | — | 월 ~$18 | ❌ ALB 기본 주소에는 ACM 인증서를 붙일 수 없다. 결국 도메인이 필요 |
| **E.** HTTP 그대로 | — | $0 | ❌ **동작하지 않음** |

## 결정: **A — CloudFront를 EC2 앞에 둔다**

```
브라우저 ──https──▶ dxxxxx.cloudfront.net ──http──▶ EC2:80 (nginx) ──▶ 앱:8080
          (AWS 관리형 인증서)              (AWS 내부 구간)
```

## 근거

1. **도메인 없이 HTTPS를 얻는 유일한 현실적 방법이다.**
   CloudFront 배포를 만들면 `dxxxxx.cloudfront.net` 주소와 인증서가 **자동으로 딸려 온다.**
   관리할 DNS도, 발급받을 인증서도 없다.

2. **인증서 갱신을 신경 쓸 필요가 없다.** certbot 크론, 갱신 실패 알림, nginx 리로드가 전부 사라진다.
   6개월 운영에서 이게 생각보다 크다.

3. **비용이 실질적으로 0이다.**
   CloudFront는 "항상 무료" 항목에 **월 1TB 전송 + 1,000만 요청**이 포함된다.
   이 트래픽으로는 근처도 못 간다. 크레딧을 거의 안 깎는다.

4. **WebSocket을 지원한다.** STOMP 연결이 그대로 통과한다(설정 주의사항은 아래).

5. **AWS 학습 목적에도 맞는다.** 오리진·캐시 정책·오리진 요청 정책·보안 그룹 제한을
   실제로 다뤄보게 된다. EKS 실습 때도 그대로 재사용할 수 있다.

## CloudFront 설정 주의사항

WebSocket과 API를 CloudFront 뒤에 두려면 **캐싱을 꺼야 한다.** 기본값이면 API 응답이 캐시된다.

| 항목 | 값 | 이유 |
| --- | --- | --- |
| Origin protocol | **HTTP only** (포트 80) | 오리진에 신뢰된 인증서가 없다. CloudFront는 self-signed 오리진 인증서를 거부한다 |
| Viewer protocol policy | **Redirect HTTP to HTTPS** | |
| Allowed methods | **GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE** | 기본값은 GET/HEAD뿐이라 **POST가 막힌다** |
| Cache policy | **CachingDisabled** | API 응답이 캐시되면 안 된다 |
| Origin request policy | **AllViewer** | `Authorization`, `Sec-WebSocket-*` 헤더가 오리진까지 가야 한다 |

**`Authorization` 헤더 전달이 특히 중요하다.** [ADR-5](#adr-5-인증-토큰-전달-방식)에서 토큰을
헤더로 보내기로 했는데, 오리진 요청 정책이 헤더를 잘라내면 **전부 401이 난다.**

### 오리진 보호

CloudFront를 붙여도 EC2 퍼블릭 IP로 직접 접근이 가능하면 의미가 반감된다.

```
보안 그룹 인바운드 80/tcp
  → 소스: AWS 관리형 프리픽스 리스트 com.amazonaws.global.cloudfront.origin-facing
```

이렇게 하면 CloudFront를 거친 요청만 들어온다.

### 남는 위험 — 오리진 구간은 평문이다

브라우저 ↔ CloudFront는 HTTPS지만 CloudFront ↔ EC2는 HTTP다.
AWS 백본 내부 구간이라 이 프로젝트에서는 수용 가능한 수준이다.
다만 **의식적으로 감수하는 것이라고 문서에 남겨 둔다.**

## `.platform/nginx.conf` 처리

CloudFront가 TLS를 담당하므로 **nginx의 443 / certbot 설정은 전부 걷어낸다.**

```nginx
map $http_upgrade $connection_upgrade {   # 현재 없음 — 모든 요청에 upgrade 를 하드코딩 중
    default upgrade;
    ''      close;
}

server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade            $http_upgrade;
        proxy_set_header Connection         $connection_upgrade;
        proxy_set_header Host               $host;
        proxy_set_header X-Forwarded-For    $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto  https;    # 원래 스킴을 앱에 알린다
    }

    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout  3600s;    # 기본 60초 → 소켓이 1분마다 끊긴다
        proxy_send_timeout  3600s;
    }
}
```

현재 설정에서 고쳐야 할 것:

- `Connection 'upgrade'`를 **모든 요청에 하드코딩**하고 있다 → `map`으로 조건부 처리
- **`proxy_read_timeout`이 없다** → 기본 60초. WebSocket이 1분마다 끊긴다
- `X-Forwarded-Proto`가 없다 → 앱이 요청을 HTTP로 인식해 리다이렉트 URL이 깨질 수 있다.
  스프링 쪽에 `server.forward-headers-strategy: framework`도 함께 설정한다

## 이 결정이 강제하는 것 — 쿠키는 쓸 수 없다

```
프론트  https://<user>.github.io/<repo>/     ← 오리진 A
API     https://dxxxxx.cloudfront.net          ← 오리진 B
```

두 오리진은 **완전한 크로스사이트**다. 게다가 `github.io`는 Public Suffix List에 올라 있어
`.github.io` 범위 쿠키는 애초에 설정할 수 없다.

- 크로스사이트 쿠키는 `SameSite=None; Secure`가 필수인데,
  **Safari는 이를 차단하므로 Safari 사용자는 로그인이 아예 안 된다.**
- 따라서 [ADR-5](#adr-5-인증-토큰-전달-방식)의 "헤더 방식"은 **선호가 아니라 유일한 선택지**가 된다.
  현재 프론트의 `axios` `withCredentials: true`도 함께 걷어내야 한다
  (`NineMensMorris_FrontEnd/src/lib/api.ts:5`).

## 함께 바꿔야 하는 것

### CORS (`SecurityConfig.java:89-90`)

```java
configuration.addAllowedOrigin("https://<user>.github.io");   // 경로 없이 오리진만
```

`https://<user>.github.io/<repo>/` 처럼 경로를 붙이면 매칭되지 않는다.
하드코딩하지 말고 `application.yml` 프로퍼티로 뺀다.

### 카카오 OAuth 리다이렉트

카카오 개발자 콘솔의 Redirect URI를 새 백엔드 주소로 등록해야 한다.

```
카카오 → https://dxxxxx.cloudfront.net/api/oauth2/kakao   (CloudFront → EC2)
백엔드 → https://<user>.github.io/<repo>/auth/callback#token=...   (프론트로 되돌림)
```

`DOMAIN` 환경변수(`OAuth2SuccessHandler.java:26`)가 두 번째 주소가 된다.
**GitHub Pages 프로젝트 페이지는 `/<repo>/` 하위 경로**이므로 그 경로까지 포함해야 한다.

### 프론트엔드 (별도 작업)

- Vite `base: '/<repo>/'`, React Router `basename` 설정
- GitHub Pages는 SPA 라우팅을 모르므로 `404.html` 폴백 트릭 필요
- 기존 `vercel.json`은 더 이상 쓰이지 않는다

---

# ADR-5. 인증 토큰 전달 방식

## 선택지

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| **A.** HttpOnly 쿠키 (현행) | XSS로 토큰 탈취 불가 | 크로스사이트면 `SameSite=None; Secure` 필요 → Safari 차단. CSRF 대책 필요. **WebSocket 핸드셰이크 외에는 STOMP 프레임에 못 실음** |
| **B.** `Authorization: Bearer` 헤더 | 오리진 무관하게 동작. CSRF 원천 무효 | 프론트가 토큰을 보관해야 함 (XSS 노출면) |

## 결정: **B — 헤더 방식으로 통일. 쿠키를 버린다**

## 근거

1. **ADR-4의 A안(같은 도메인)을 택하더라도 헤더가 더 단순하다.**
   쿠키를 쓰면 CSRF 대책이 따라붙는데(현재 `csrf().disable()`로 꺼둔 상태다),
   헤더 방식은 그 문제 자체가 없다.

2. **WebSocket 때문에 어차피 헤더가 필요하다.**
   브라우저의 `WebSocket` API는 커스텀 헤더를 붙일 수 없어서,
   지금은 핸드셰이크 HTTP 요청의 쿠키에 의존하고 있다
   (그래서 [01](01-code-audit.md) P1-14의 `Principal`이 우연히 채워진다).
   **STOMP `CONNECT` 프레임 헤더에 토큰을 싣고 `ChannelInterceptor`에서 인증하는 방식**이
   전송 계층과 무관하게 동작하고, 게스트 모드에서도 동일하게 쓸 수 있다.

   ```java
   @Override
   public Message<?> preSend(Message<?> message, MessageChannel channel) {
       StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
       if (StompCommand.CONNECT.equals(accessor.getCommand())) {
           String token = accessor.getFirstNativeHeader("Authorization");
           accessor.setUser(authenticate(token));   // 실패 시 예외 → 연결 거부
       }
       return message;
   }
   ```

3. **XSS 노출은 관리 가능하다.**
   프론트가 서드파티 스크립트 없는 정적 SPA이고, CSP 헤더로 스크립트 출처를 제한할 수 있다.
   반대로 쿠키 방식은 **Safari 사용자가 로그인 자체를 못 하는** 문제라 회피할 방법이 없다.

4. 토큰은 **메모리(변수)에 두는 것을 기본**으로 하고,
   새로고침 유지가 필요하면 `sessionStorage`를 쓴다. `localStorage`는 마지막 선택지다.

## 따라오는 변경

- `OAuth2SuccessHandler`가 쿠키를 굽지 않고, **프론트 URL에 토큰을 담아 리다이렉트**한다.
  (`https://example.com/auth/callback#token=...` — 쿼리스트링이 아니라 프래그먼트를 쓰면 서버 로그에 남지 않는다)
- `JwtAuthenticationFilter`의 `parseTokenFromCookie` → `Authorization` 헤더 파싱으로 교체
- `SecurityConfig`의 `deleteCookies("access_token")`, `LogoutService` 제거
- `Cookie.setMaxAge` 단위 버그([01](01-code-audit.md) P1-3)가 함께 사라진다

---

# ADR-6. Redis를 어디에 쓸 것인가

Redis 도입은 확정이다(학습 목적 포함). 남는 질문은 **무엇에 쓸 것인가**다.
억지로 끼워 넣으면 복잡도만 늘어나므로, **지금 실제로 있는 문제를 푸는 용도**만 고른다.

## 채택 — 실제 문제를 푼다

### ① 진행 중인 게임 상태 (EKS 단계에서 필수)

지금은 `MorrisService`의 `HashMap` 11개에 있다([01](01-code-audit.md) P0-8).
EC2 단독이면 인메모리로 충분하지만, **EKS에서는 파드가 죽으면 게임이 사라진다**(ADR-2 표의 4·5번).

```
KEY  room:{roomId}:state    → 직렬화된 게임 상태 (JSON 또는 MessagePack)
TTL  2시간 — 방치된 방이 자동으로 사라진다
```

- **원자성**: 착수는 read-modify-write다. Lua 스크립트 또는 `WATCH`/`MULTI`로 처리한다.
  Spring Data Redis에서 `RedisTemplate.execute(RedisScript)`.
- **주의**: 착수마다 Redis 왕복이 생긴다. 이 트래픽에서는 무시할 수준이지만,
  **인메모리보다 느려진다는 점은 사실이다.** EC2 단계에서는 로컬 캐시로 두고
  EKS 단계에 Redis로 전환하는 **인터페이스 분리**([06](06-persistence-and-queries.md) 2절의 `RoomRegistry`)가 답이다.

### ② 랭킹 — Sorted Set

Redis의 교과서적 용례이고 실제로 잘 맞는다.

```
ZADD    leaderboard {rating} {userId}      점수 갱신
ZREVRANGE leaderboard 0 99 WITHSCORES      상위 100명
ZREVRANK  leaderboard {userId}             "내 순위" ← 지금 SQL로는 못 구한다
```

- 현재 랭킹 조회는 전체 테이블 스캔이다([06](06-persistence-and-queries.md) 3-1).
- **"내 등수"는 지금 아예 없는 기능**이다. SQL로 하려면 윈도우 함수나 카운트 쿼리가 필요한데
  `ZREVRANK`는 O(log N) 한 방이다.
- **정본은 MySQL**로 두고 Redis는 **읽기 뷰**로 쓴다. 부팅 시 DB에서 ZSET을 재구성한다.
  Redis가 날아가도 데이터를 잃지 않는다.

### ③ 리프레시 토큰 / 로그아웃 무효화

현재 [01](01-code-audit.md) P1-6·P1-7 — **로그아웃해도 토큰이 살아 있다.**
리프레시 토큰이 생성만 되고 저장되는 곳이 없어서 폐기할 방법이 없다.

```
KEY  refresh:{userId}:{jti}  → 토큰 메타   TTL = 리프레시 토큰 수명
KEY  blacklist:{jti}         → "1"        TTL = 액세스 토큰 잔여 수명
```

Redis의 TTL이 이 문제에 정확히 맞는다. RDB로 하면 만료 토큰 청소 배치를 따로 짜야 한다.

### ④ 게스트 발급 레이트 리밋

[08](08-guest-mode-design.md)에서 필요하다. 게스트 발급을 막지 않으면 DB에 행이 무한 생성된다.

```
INCR  ratelimit:guest:{ip}    →  첫 호출 시 EXPIRE 3600
```

## 기각 — 넣지 않는다

| 용도 | 기각 이유 |
| --- | --- |
| **STOMP 브로커 릴레이** | Spring의 SimpleBroker는 Redis로 대체되지 않는다. 진짜 외부 브로커가 필요하면 RabbitMQ/ActiveMQ다. 파드 간 브로드캐스트만 필요하면 Redis Pub/Sub으로 자체 구현이 가능하지만, **`replicas: 1`로 두면 필요 없다.** 이 트래픽에 다중 파드는 과하다 |
| **세션 저장소** (`spring-session-data-redis`) | 세션을 안 쓴다. `SessionCreationPolicy.STATELESS` |
| **DB 조회 캐시** | 캐시할 만큼 쿼리가 무겁지 않다. [06](06-persistence-and-queries.md)의 인덱스·프로젝션 개선이 먼저다 |
| **방 목록(로비)** | 인메모리로 충분하고, 실시간 갱신은 어차피 STOMP로 푸시한다 |

## 도입 순서

**Redis를 먼저 넣고 나서 코드를 고치면 안 된다.**
③·④는 언제든 넣을 수 있고, ①은 [06](06-persistence-and-queries.md)의 `RoomRegistry`로
상태를 한 객체에 모으는 리팩터링이 **끝난 뒤에** 구현체만 갈아 끼우는 방식이 맞다.

```
1. 상태를 RoomRegistry 인터페이스 뒤로 모은다 (구현은 인메모리)
2. Redis 도입 — ③ 토큰 무효화, ④ 레이트 리밋 (독립적, 위험 낮음)
3. ② 랭킹 ZSET (읽기 뷰이므로 실패해도 DB 폴백)
4. ① RedisRoomRegistry 구현 → EKS 단계에서 전환
```

---

# ADR-7. 메시징 브로커 — Kafka? RabbitMQ?

## 결론: **둘 다 EC2 상시 운영에는 넣지 않는다. Kafka는 이 프로젝트에 맞지 않는다.**

## 1. Kafka는 STOMP 문제를 못 푼다 — 이게 결정적이다

여러 파드에서 STOMP 브로드캐스트를 하려면 Spring의 **외부 브로커 릴레이**가 필요하다.

```java
registry.enableStompBrokerRelay("/topic", "/queue")   // SimpleBroker 대체
        .setRelayHost(...).setRelayPort(61613);
```

이 기능은 **STOMP 프로토콜을 말하는 브로커**만 지원한다 — RabbitMQ(STOMP 플러그인), ActiveMQ/Artemis.
**Kafka는 STOMP를 말하지 않아서 브로커 릴레이로 쓸 수 없다.**
Kafka로 하려면 "컨슈머가 메시지를 받아 `SimpMessagingTemplate`으로 다시 뿌리는" 브리지를
직접 짜야 하는데, 그건 Kafka를 쓰는 게 아니라 Kafka **위에 브로커를 다시 만드는** 일이다.

## 2. 메모리가 안 들어간다

| 프로세스 | RSS |
| --- | --- |
| 앱 JVM | ~600MB |
| MySQL 8 (튜닝) | ~300MB |
| Redis | ~50MB |
| OS + Docker | ~150MB |
| **소계** | **~1.1GB** (t4g.small 2GiB 중) |
| **+ Kafka (KRaft, 힙 1GB 권장 / 최소 512MB)** | **+700MB ~ 1.2GB** → **초과** |
| + RabbitMQ | +100~150MB → 들어감 |

Kafka를 넣으려면 t4g.medium(4GiB)로 올려야 하고, 6개월 +$73다.
[ADR-2](#adr-2-실행-플랫폼--eks를-6개월-상시-운영할-수-있는가)에서 예산이 $196/$200으로 이미 빠듯하다.
관리형(MSK)은 최소 구성도 월 $60+라 논외다.

## 3. 풀 문제가 없다

Kafka의 값어치는 **높은 처리량 · 이벤트 재생 · 여러 컨슈머 그룹**에서 나온다.

| Kafka의 강점 | 이 프로젝트 |
| --- | --- |
| 초당 수만 건 처리 | 하루 이벤트가 수십 건 |
| 이벤트 로그 재생 | 재생할 컨슈머가 없다 |
| 여러 컨슈머 그룹 | 컨슈머가 0개 |
| 서비스 간 비동기 결합 해제 | 서비스가 1개 |

[03](03-layering-and-dto.md) ❺에서 지적한 "게임 종료 ↔ 점수 갱신 결합"은
`ApplicationEventPublisher` 한 줄로 끊긴다. Kafka는 그 문제에 대한 답으로는 1000배 크다.

## 4. "Redis로 되는데 왜 Kafka를 쓰나"

먼저 짚을 것 — **Redis에는 메시징이 두 가지 있고, 둘의 성격이 완전히 다르다.**

| | Redis Pub/Sub | Redis Streams | Kafka |
| --- | --- | --- | --- |
| 메시지 보관 | **안 함** (그 순간 없으면 영영 못 받음) | 함 (메모리) | 함 (**디스크**) |
| 컨슈머 그룹 | 없음 | 있음 (`XREADGROUP`) | 있음 |
| ACK / 재처리 | 없음 | 있음 (`XACK`, pending list) | 있음 (offset) |
| 보존 용량 | — | **RAM 크기까지** | **디스크 크기까지 (TB급)** |
| 보존 기간 | 0 | 보통 수 시간~하루 | **수일~수주가 기본** |
| 처리량 | 높음 | 높음 | **매우 높음** (파티션 수평 확장) |
| 과거 데이터 리플레이 | 불가 | 제한적 | **가능 (offset을 되감음)** |

**"Redis로 충분하다"의 범위는 실제로 꽤 넓다.**
컨슈머 그룹·ACK·재처리가 필요해도 **Redis Streams면 대부분 해결된다.**
소규모 서비스에서 Kafka를 걷어내고 Redis Streams로 바꿔도 아무 일이 안 일어나는 경우가 많다.

### Kafka가 실제로 이기는 지점은 네 가지다

1. **보존 용량과 기간.** 이게 가장 근본적인 차이다.
   Redis는 **데이터가 RAM에 들어가야 한다.** 이벤트를 2주치 쌓으려면 그만한 메모리를 사야 한다.
   Kafka는 디스크에 순차 기록하므로 **수백 GB를 몇 주 보관하는 게 정상 운영**이다.

2. **리플레이.** 컨슈머 버그를 고친 뒤 **지난 3일치를 처음부터 다시 처리**할 수 있다.
   offset을 되감기만 하면 된다. Redis Streams는 메모리에 남아 있는 만큼만 가능하다.

3. **독립적인 여러 소비 시스템.** 같은 이벤트 로그를 검색 색인, 데이터 웨어하우스,
   알림 서비스가 **각자 다른 속도로** 읽는다. 느린 컨슈머가 빠른 컨슈머를 막지 않고,
   나중에 컨슈머를 하나 추가하면 **과거 데이터부터 읽어 들일 수 있다.**

4. **생태계.** Kafka Connect(DB→Kafka→S3), Debezium(MySQL binlog CDC),
   Kafka Streams / ksqlDB. Redis에는 이에 대응하는 게 없다.

### 그래서 실무에서 Kafka를 쓰는 진짜 이유

메시지 큐가 필요해서가 아니라, **여러 팀·여러 시스템이 같은 이벤트를 각자 목적으로 소비하는
"중앙 이벤트 로그"가 필요해서**인 경우가 대부분이다.
기술 문제라기보다 **조직과 데이터 흐름의 문제**에 가깝다.

> 컨슈머가 하나뿐이고, 처리량이 낮고, 과거 데이터를 다시 볼 일이 없다면
> **Kafka를 쓸 이유가 없다.** 이 프로젝트가 정확히 거기에 해당한다
> (하루 이벤트 수십 건, 컨슈머 0개, 서비스 1개).

### 판단 기준으로 정리하면

```
메시지를 잃어도 되는가?            → Redis Pub/Sub
잃으면 안 되지만 하루치면 되는가?   → Redis Streams
며칠~몇 주 보존 + 리플레이 +
독립 컨슈머 여러 개 + 높은 처리량?  → Kafka
서비스가 하나뿐인가?               → ApplicationEventPublisher (지금 여기)
```

## 5. Kafka를 배우고 싶다면 — 이 프로젝트 밖에서

이 서비스에 Kafka를 넣으면 **"왜 필요한지 모르겠는데 있는 것"** 이 되어
오히려 학습에 방해가 된다. Kafka의 값어치는 위 4가지 상황에서만 체감되는데,
여기서는 그 상황을 인위적으로 만들어야 하고, 그러면 배우는 게 왜곡된다.

**Kafka를 제대로 느낄 수 있는 시나리오**는 따로 만드는 편이 낫다.

| 시나리오 | Kafka다운 이유 |
| --- | --- |
| **Debezium CDC** — MySQL binlog → Kafka → 다른 저장소 동기화 | 리플레이·순서 보장·Connect 생태계를 전부 쓴다 |
| 로그/클릭스트림 수집 파이프라인 | 처리량과 보존 기간이 실제로 문제가 된다 |
| 컨슈머 3개 이상이 같은 토픽을 각자 속도로 소비 | 컨슈머 그룹·offset의 존재 이유가 드러난다 |

학습 방법:

- **로컬 docker-compose에서** — 비용 0. Kafka 자체를 익히는 데는 이게 가장 효율적이다.
- **EKS 한 달 동안 Strimzi Operator로** — 쿠버네티스 오퍼레이터 학습과 겹쳐 일석이조.
  단 노드를 t4g.medium 이상으로 올려야 하므로 **예산 재확인이 먼저다.**

이 프로젝트에 굳이 붙인다면 그나마 말이 되는 형태는 이것뿐이다.

```
게임 종료  →  topic: match.finished
                  ├─ consumer A: matches 테이블 저장
                  ├─ consumer B: 랭킹 ZSET 갱신
                  └─ consumer C: 일별 통계 집계
```

컨슈머가 3개라 "왜 Kafka인가"에 최소한의 답은 된다.
**다만 이건 학습 과제이지 이 서비스에 필요한 구조가 아니다.** 문서에 그렇게 적어 둔다.

## 5. RabbitMQ는 언제 넣나

**EKS에서 `replicas: 2` 이상을 실제로 돌려보고 싶을 때만.**

파드가 2개면 A파드에 붙은 플레이어와 B파드에 붙은 플레이어가 서로의 메시지를 못 받는다.
그때 `enableStompBrokerRelay` + RabbitMQ STOMP 플러그인이 **정확히 그 문제를 푼다.**
메모리도 100~150MB라 감당된다.

다만 [ADR-6](#adr-6-redis를-어디에-쓸-것인가)에서 정한 대로 **`replicas: 1`이면 필요 없다.**
"파드를 늘려보는 것" 자체가 학습 목표라면 그때 도입하고, 아니면 넣지 않는다.

## 요약

| 후보 | EC2 5개월 | EKS 1개월 | 근거 |
| --- | --- | --- | --- |
| **Kafka** | ❌ | △ (학습 과제로 분리) | STOMP 릴레이 불가 · 메모리 초과 · 풀 문제 없음 |
| **RabbitMQ** | ❌ | ○ (`replicas ≥ 2` 실습 시) | 브로커 릴레이 정식 지원 · 메모리 감당 가능 |
| **Spring `ApplicationEventPublisher`** | ✅ | ✅ | 지금 필요한 결합 해제는 이걸로 충분 |

---

# 결정 요약

| ADR | 결정 |
| --- | --- |
| 1. 모듈 | **4모듈** — `morris-core`(순수) / `morris-storage` / `morris-support` / `morris-api`. `auth·game·user`는 api 내부 패키지로 두고 추후 승격 |
| 2. 플랫폼 | **EC2 5개월 + EKS 1개월** (겹치지 않게). EKS 상시 운영은 크레딧 3배 초과. EKS 전에 앱 결함 7가지 선정리 |
| 3. DB | **MySQL 8 유지**, t4g.small(2GiB)에 앱·MySQL·Redis 동거. 1GiB로는 OOM |
| 4. HTTPS | **CloudFront** (`*.cloudfront.net` 관리형 인증서). 도메인·certbot 불필요. 오리진은 보안그룹으로 CloudFront만 허용 |
| 5. 토큰 | **`Authorization: Bearer` 헤더**. 쿠키 폐기. STOMP는 CONNECT 프레임 헤더 |
| 6. Redis | **게임 상태 · 랭킹 ZSET · 토큰 무효화 · 레이트 리밋** 4가지만. 브로커 릴레이·세션·캐시는 기각 |
| 7. 메시징 | **Kafka·RabbitMQ 모두 미도입.** `ApplicationEventPublisher`로 충분. Kafka는 별도 학습 과제로 분리 |
