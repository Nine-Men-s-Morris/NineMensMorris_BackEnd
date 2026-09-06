# 10. 로깅 · 관측성

원칙: **운영자가 알아야 할 것만 남긴다.** 도배되는 로그는 없는 로그와 같다.

---

## 1. 현재 상태 — 전체 로그 구문이 6줄이다

| 위치 | 레벨 | 내용 | 판정 |
| --- | --- | --- | --- |
| `JwtAuthenticationFilter.java:48` | `error` | `"JWT token validation failed"` | ❌ 정상 흐름 |
| `JwtAuthenticationFilter.java:70` | `error` | `"Failed to process JWT token"` + 스택트레이스 | ⚠️ 예외를 삼키면서 로그만 남김 |
| `JwtProvider.java:72` | `error` | `"Failed to validate JWT token: {}"` | ❌ 위와 **같은 사건을 두 번** 찍는다 |
| `CustomOAuth2UserService.java:32` | `error` | `"Failed to oAuth2User: {}"` | ❌ **주석 처리된 코드**를 감싼 try/catch |
| `WebSocketEventListener.java:29` | `info` | `"소켓에 연결되었습니다..."` | ⚠️ 도배 위험 |
| `WebSocketEventListener.java:40` | `info` | `"소켓 연결이 끊겼습니다..."` | ⚠️ 도배 위험 |
| `MorrisStatus.java:26, 30` | `System.out` | `"게임이 진행 중입니다."` | ❌ sout. 게다가 데드코드 |

**6줄 중 4줄이 오용이다.** 그리고 다음이 전부 빠져 있다.

### 게임 도메인 로그가 0줄이다

`MorrisService`에는 `@Slf4j`조차 없다.
**게임 시작·종료·승자·점수 변동·기권·무승부 — 아무것도 기록되지 않는다.**

[01](01-code-audit.md)의 P0-1(승패 점수가 뒤집혀 있음)이 오래 발견되지 않은 직접적 원인이 이것이다.
점수가 이상하다는 신고가 들어와도 **확인할 방법이 없다.**

### 그 외

| 빠진 것 | 영향 |
| --- | --- |
| 상관관계 ID (MDC) | 어느 요청·어느 방의 로그인지 묶을 키가 없다 |
| 에러 응답의 추적 키 | 사용자가 "에러 났어요" 해도 로그를 찾을 수 없다 |
| `logback-spring.xml` | 기본 콘솔 출력만. 환경별 구분 없음 |
| Actuator | 헬스체크·메트릭 엔드포인트 자체가 없다 ([01](01-code-audit.md) P2-17) |

### 레벨 오용이 만드는 실제 피해

[01](01-code-audit.md) P1-3 때문에 **쿠키가 JWT보다 1000배 오래 산다.**
그래서 만료된 토큰을 든 브라우저가 계속 요청을 보내고,
그때마다 `JwtProvider`와 `JwtAuthenticationFilter`가 **각각 ERROR를 찍는다.**
사용자 한 명이 탭을 열어두기만 해도 ERROR 로그가 수천 줄 쌓인다.
그 사이에 진짜 ERROR가 묻힌다.

---

## 2. 로그 레벨 정책

> **기준: "이 줄을 보고 운영자가 무언가 할 게 있는가?"**

| 레벨 | 기준 | 이 프로젝트의 예 |
| --- | --- | --- |
| **ERROR** | 사람이 개입해야 한다. 알림 대상 | DB 커넥션 실패, 점수 갱신 트랜잭션 실패, 예상 못 한 예외, 게임 상태 정합성 붕괴 |
| **WARN** | 자동 복구됐지만 **반복되면** 문제 | 게임 중 소켓 끊김, 규칙 위반 요청 거절, 방 입장 경합 실패, 레이트 리밋 차단 |
| **INFO** | 비즈니스 사건. **하루치를 읽으면 서비스 상태가 보인다** | 게임 시작/종료(승자·레이팅 변동), 회원 가입, 게스트 발급, 유휴 정리 배치 결과, 애플리케이션 기동/종료 |
| **DEBUG** | 장애 재현용. 운영에서는 끈다 | 개별 착수 좌표, 소켓 연결/해제, JWT 검증 실패 |
| **TRACE** | 쓰지 않는다 | |

### 절대 찍지 않는 것

- 매 요청의 JWT 검증 결과 (정상 흐름)
- 정상적인 소켓 연결/해제를 INFO로 (DEBUG로 내린다)
- 메서드 진입/이탈
- DTO 전체 덤프
- **토큰·쿠키 원문, 비밀번호, 이메일, 카카오 회원번호**
  → 현재 `log.error("Failed to process JWT token", exception)`은 스택트레이스에 토큰이 섞일 수 있다

### 로그량 추정

| 방식 | 하루 로그량 |
| --- | --- |
| 현재 (요청마다 ERROR 2줄) | 사용자 1명이 탭만 열어둬도 **수천 줄** |
| 제안 (게임당 INFO 2줄) | 하루 100판이어도 **200줄** |

---

## 3. 무엇을 남길 것인가 — 게임 도메인

**게임 한 판 = INFO 2줄.** 이 두 줄로 "누가 누구와 언제 어떻게" 가 재구성되어야 한다.

```java
// 시작
log.info("게임 시작 roomId={} black={} white={} 선공={} 랭크전={}",
         roomId, blackId, whiteId, firstMove, ranked);

// 종료 — 승자·사유·레이팅 변동이 한 줄에 다 있어야 한다
log.info("게임 종료 roomId={} 결과={} 승자={} 사유={} 수={} 레이팅 {}({}) / {}({})",
         roomId, outcome, winnerId, endReason, moveCount,
         blackId, formatDelta(blackDelta), whiteId, formatDelta(whiteDelta));
```

`레이팅 1042(+16) / 1058(-16)` 형태면 **P0-1 같은 부호 뒤집힘을 로그만 봐도 알 수 있다.**

WARN으로 남길 것:

```java
log.warn("규칙 위반 요청 거절 roomId={} userId={} 사유={} 요청={}",
         roomId, actorId, reason, move);       // 반복되면 클라이언트 버그 또는 치팅
log.warn("게임 중 연결 끊김 roomId={} userId={} 재접속 대기 30초", roomId, userId);
```

**규칙 위반 거절 로그는 특히 중요하다.**
[02](02-game-rules-audit.md)의 검증을 서버에 넣고 나면,
이 WARN이 계속 찍힌다 = 프론트와 서버의 규칙 구현이 어긋나 있다는 뜻이다.
지금은 프론트에만 규칙이 있어서 그런 신호를 받을 방법이 없다.

---

## 4. MDC — 로그를 묶는 키

로그 한 줄만 봐서는 어느 요청인지 알 수 없다. MDC로 컨텍스트를 붙인다.

| 키 | 값 |
| --- | --- |
| `traceId` | 요청/메시지마다 생성하는 8자리 (에러 응답에도 같은 값을 담는다) |
| `userId` | 인증된 사용자 id (게스트 포함) |
| `roomId` | 게임 관련 처리에만 |

**HTTP와 STOMP 양쪽에 넣어야 한다.**

```java
// HTTP — OncePerRequestFilter
MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
try { chain.doFilter(req, res); } finally { MDC.clear(); }   // finally 필수. 스레드 풀이 재사용된다
```

```java
// STOMP — 서블릿 필터가 안 걸린다. ChannelInterceptor 로 따로 넣어야 한다
// [08](08-guest-mode-design.md) 8절의 인증 인터셉터와 같은 자리
```

**STOMP 쪽을 빼먹기 쉽다.** 그런데 이 서비스의 핵심 로직이 전부 STOMP다.
서블릿 필터만 넣으면 정작 필요한 곳에 컨텍스트가 안 붙는다.

### 에러 응답에 `traceId` 담기

```json
{ "code": "NOT_YOUR_TURN", "message": "상대 차례입니다.", "traceId": "8f3c1a90" }
```

현재 `CustomErrorResponse`는 `status`/`name`/`message`뿐이다([05](05-api-and-protocol.md)).
`traceId`가 있으면 사용자가 화면 값을 알려주는 것만으로 로그를 특정할 수 있다.

---

## 5. 설정

`logback-spring.xml`이 없다. 환경별로 나눈다.

```xml
<configuration>
  <springProfile name="local">
    <!-- 사람이 읽는 컬러 콘솔 -->
    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
    <logger name="com.ninemensmorris" level="DEBUG"/>
  </springProfile>

  <springProfile name="prod">
    <!-- 컨테이너이므로 stdout. 파일로 쓰지 않는다 -->
    <root level="INFO">
      <appender-ref ref="JSON"/>
    </root>
    <logger name="com.ninemensmorris" level="INFO"/>
    <logger name="org.hibernate.SQL" level="WARN"/>
  </springProfile>
</configuration>
```

**패턴에 MDC를 포함한다.**

```
%d{HH:mm:ss.SSS} %-5level [%X{traceId}] [%X{userId}] %logger{20} - %msg%n
```

### 컨테이너에서는 stdout으로

파일로 쓰면 EBS를 채우고 컨테이너 재시작 시 사라진다. stdout으로 내보내고
**Docker 로그 로테이션**을 건다. 이걸 안 하면 EBS가 찬다.

```yaml
logging:
  driver: json-file
  options: { max-size: "10m", max-file: "3" }
```

### CloudWatch Logs

수집 5GB/월까지 무료다. 위 로그량이면 근처도 못 간다.
다만 **프리티어 크레딧을 조금이라도 아끼려면 초기에는 붙이지 않아도 된다.**
`docker logs`로 충분하다. EKS 단계에서 붙이는 편이 학습 순서로도 맞다.

---

## 6. Actuator

현재 `spring-boot-starter-actuator`가 없다. 헬스체크 엔드포인트가 아예 없어서
CloudFront·nginx·Docker·쿠버네티스 어느 쪽도 상태를 판단할 수 없다.

```yaml
management:
  endpoints.web.exposure.include: health,info,metrics
  endpoint.health:
    probes.enabled: true          # /actuator/health/liveness, /readiness
    show-details: never           # 외부에 DB 정보를 노출하지 않는다
```

- `/actuator/health`만 열고 나머지는 인증 뒤로 ([08](08-guest-mode-design.md) 7절 인가 규칙).
- `livenessProbe` / `readinessProbe`에 그대로 쓴다 —
  [07](07-architecture-decision.md) ADR-2의 "EKS 전 선정리 3번".
- docker-compose `healthcheck`, `depends_on: condition: service_healthy`에도 쓴다
  ([01](01-code-audit.md) P2-13).

**메트릭은 지금 단계에서 Prometheus까지 갈 필요가 없다.**
`/actuator/metrics`로 JVM 메모리와 커넥션 풀만 확인할 수 있으면 충분하다.
1GiB~2GiB 인스턴스에서는 **힙 사용량과 HikariCP 활성 커넥션 수**가 가장 자주 보게 될 값이다.

---

## 7. LGTM 스택 (Loki · Grafana · Tempo · Mimir)

### 결론: **EC2 기간엔 Grafana Cloud 무료 티어, EKS 한 달엔 자체 호스팅**

### 왜 전부 자체 호스팅하지 않나 — 메모리가 없다

| 컴포넌트 | 역할 | 대략 RSS |
| --- | --- | --- |
| Grafana | 시각화 | 100~150MB |
| Loki (single-binary) | 로그 | 100~200MB |
| Tempo | 트레이스 | 100~200MB |
| **Mimir** | 메트릭 (장기 저장) | **500MB~1GB** |
| Prometheus (Mimir 대체) | 메트릭 | 150~300MB |

[07](07-architecture-decision.md) ADR-3의 예산이 이미 이렇다.

```
앱 600 + MySQL 300 + Redis 50 + OS 150 = 1.1GB  /  t4g.small 2GiB
```

**남는 게 약 900MB인데 풀 LGTM은 최소 800MB~1.5GB다.** 넣으면 앱이 죽는다.
Mimir를 Prometheus로 바꾸고 Tempo를 빼도 350~450MB라 여전히 아슬아슬하다.

인스턴스를 t4g.medium으로 올리면 되지만 6개월 +$73 —
[07](07-architecture-decision.md) ADR-2의 예산($196/$200)이 깨진다.

### EC2 기간: Grafana Cloud 무료 티어

**메모리 비용 0, 금전 비용 0.** 인스턴스에는 수집 에이전트만 올린다.

```
EC2
 ├─ 앱 → /actuator/prometheus  ─┐
 ├─ Docker 로그 (stdout)       ─┼─▶ Grafana Alloy (~50~100MB) ──▶ Grafana Cloud
 └─ MySQL / Redis exporter     ─┘                                  (Loki + Prometheus + Grafana)
```

- 무료 티어에 메트릭·로그·트레이스가 모두 포함되고 보존 기간이 짧다(수 주).
  6개월 개인 프로젝트에는 넉넉하다.
- **Grafana 대시보드, LogQL, PromQL, 알림 규칙을 그대로 배운다.**
  자체 호스팅과 배우는 내용이 거의 같다. 다른 건 "누가 운영하느냐"뿐이다.
- 계정을 하나 만들면 되고, 인스턴스가 죽어도 **과거 로그가 남는다** —
  자체 호스팅에서는 앱과 로그 수집기가 같이 죽는다.

### EKS 한 달: 자체 호스팅

여기서는 **자체 호스팅 자체가 학습 목표**다. 노드가 t4g.medium(4GiB)이므로 여유도 조금 생긴다.

```
kube-prometheus-stack (Helm)   Prometheus + Grafana + Alertmanager
loki-stack (Helm)              Loki + Promtail
```

- `ServiceMonitor` / `PodMonitor` CRD, Helm values, PVC, 오퍼레이터 패턴을 실제로 다룬다.

#### 무엇이 어떤 워크로드로 올라가는가

"파드로 띄운다"는 결과를 말하는 표현이다. 파드를 직접 만들지 않고
**워크로드 리소스**를 선언하면 그것이 파드를 만든다. 정확히는 이렇게 부른다.

- 설치 단위 → **Helm 차트로 설치한다** (`helm install monitoring prometheus-community/kube-prometheus-stack`)
- 배포 단위 → **Deployment / StatefulSet / DaemonSet으로 올린다**

| 컴포넌트 | 워크로드 | 이유 |
| --- | --- | --- |
| Grafana | **Deployment** | 상태 없음 (대시보드는 ConfigMap 또는 PVC) |
| kube-state-metrics | Deployment | |
| Prometheus Operator | Deployment | |
| Prometheus | **StatefulSet** | TSDB 볼륨이 파드에 고정돼야 함 |
| Alertmanager | StatefulSet | 클러스터링 시 고정 신원 필요 |
| Loki (single-binary) | StatefulSet | |
| node-exporter · Promtail | **DaemonSet** | **노드마다 정확히 하나** |

Prometheus와 Alertmanager는 StatefulSet을 직접 쓰지 않는다.
**`Prometheus` / `Alertmanager` CR(커스텀 리소스)을 선언하면 오퍼레이터가 StatefulSet을 만든다.**
그래서 이 둘은 "CR로 선언한다"가 더 정확한 표현이다.

**파드 이름만 봐도 워크로드 종류를 알 수 있다.**

```
monitoring-grafana-547db5d97-xxxxx      해시 2개  → Deployment (ReplicaSet 경유)
prometheus-monitoring-kube-...-0        끝이 -0   → StatefulSet
monitoring-prometheus-node-ex...-xxxxx  해시 1개  → DaemonSet
```

접두사(`monitoring-`)는 **Helm 릴리스 이름**이다.
- **Mimir와 Tempo는 넣지 않는다.**
  - Mimir는 Prometheus의 장기 저장·수평 확장용이다. 한 달 실습에 보존 기간이 문제될 일이 없다.
  - Tempo(분산 추적)는 **서비스가 하나뿐이라 값어치가 거의 없다.**
    분산 추적은 요청이 여러 서비스를 건너갈 때 의미가 있는데, 여기선 건너갈 곳이 없다.
    4절의 MDC `traceId`로 충분하다.
- 즉 실질적으로는 **"LG" 스택 (Loki + Grafana + Prometheus)** 이다.
  이름에 끌려 Mimir/Tempo까지 넣으면 메모리만 먹고 배우는 건 없다.

### 앱 쪽 선행 작업

| # | 작업 | 비고 |
| --- | --- | --- |
| 1 | `spring-boot-starter-actuator` | 6절 |
| 2 | `micrometer-registry-prometheus` | `/actuator/prometheus` 노출 |
| 3 | **prod 로그를 JSON으로** | 5절. Loki가 필드로 파싱할 수 있어야 라벨 검색이 된다 |
| 4 | MDC를 JSON 필드로 내보내기 | `traceId`, `userId`, `roomId`가 Loki 라벨/필드가 된다 |

3번이 특히 중요하다. **평문 로그를 Loki에 넣으면 전문 검색밖에 못 한다.**
JSON으로 내보내야 `{roomId="42"}` 같은 질의가 된다.

### 무엇을 계측할 것인가 — 이 서비스에 실제로 쓸모 있는 것만

JVM 기본 메트릭(힙, GC, 스레드)과 HikariCP는 Actuator가 자동으로 준다.
**직접 만들 값은 게임 도메인 쪽이다.**

```java
Gauge.builder("morris.rooms.active", registry, r -> roomRegistry.size())      // 진행 중인 방 수
Gauge.builder("morris.sockets.connected", ...)                                 // 동시 접속 소켓
Counter "morris.games.finished" tags(reason)                                   // NORMAL/RESIGN/DRAW/TIMEOUT
Counter "morris.moves.rejected" tags(reason)                                   // ← 가장 중요
Timer   "morris.move.duration"                                                 // 착수 처리 시간
```

**`morris.moves.rejected`가 핵심이다.**
[02](02-game-rules-audit.md)의 규칙 검증을 서버에 넣고 나면, 이 카운터가 올라간다는 건
**프론트와 서버의 규칙 구현이 어긋났거나 누군가 조작을 시도한다**는 뜻이다.
3절의 WARN 로그와 같은 사건을 메트릭으로도 보는 것이고,
알림을 걸 수 있는 건 로그가 아니라 이쪽이다.

`morris.rooms.active`는 [06](06-persistence-and-queries.md) 2절의 메모리 누수
(게임 종료 후 맵이 정리되지 않는 문제)를 **그래프로 바로 확인**할 수 있게 해준다.
계속 우상향하면 정리가 안 되고 있다는 뜻이다.

### 알림

무료 티어에서도 알림이 된다. **처음엔 3개면 충분하다.**

| 알림 | 조건 |
| --- | --- |
| 앱 다운 | `/actuator/health` 실패 2분 지속 |
| 힙 부족 | 힙 사용률 85% 초과 5분 지속 (2GiB 인스턴스에서 가장 현실적인 사고) |
| 규칙 위반 급증 | `morris.moves.rejected` 5분간 20건 초과 |

**알림을 많이 만들면 아무도 안 본다.** 로그 레벨 정책(2절)과 같은 원칙이다.

> 참고: 코드 분석 서비스였던 **lgtm.com은 2022-12에 종료**되어 GitHub Code Scanning(CodeQL)으로
> 흡수됐다. 정적 분석이 목적이라면 그쪽을 쓴다 — 공개 리포지토리는 무료다.

---

## 8. 정리 작업 목록

| # | 작업 |
| --- | --- |
| 1 | `MorrisStatus`의 `System.out.println` 제거 (클래스 자체가 데드코드) |
| 2 | `JwtProvider` / `JwtAuthenticationFilter`의 중복 ERROR → 한 곳에서 `debug` 1회로 |
| 3 | `CustomOAuth2UserService`의 주석을 감싼 try/catch 삭제 |
| 4 | 소켓 연결/해제 로그 `info` → `debug`. **단, 게임 중 끊김은 `warn`으로 남긴다** |
| 5 | `MorrisService`에 게임 시작·종료 INFO 2줄 추가 (3절) |
| 6 | 규칙 위반 거절 WARN 추가 |
| 7 | MDC 필터 (HTTP + STOMP) |
| 8 | `CustomErrorResponse`에 `traceId` 추가 |
| 9 | `logback-spring.xml` 추가 (local/prod) |
| 10 | Actuator 추가 + 헬스체크 연결 |
| 11 | Docker 로그 로테이션 설정 |

**1~4는 지우거나 레벨만 바꾸는 작업**이라 즉시 할 수 있다.
5~6이 실질적으로 가장 값어치가 큰 작업이다.
