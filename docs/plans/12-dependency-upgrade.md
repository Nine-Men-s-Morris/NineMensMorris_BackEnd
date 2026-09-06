# 12. 의존성 업그레이드

기준일: **2026-09-06**. 현재 빌드는 2024-04 시점에 고정되어 있다.

---

## 1. 현황과 목표

| 항목 | 현재 | 최신 (2026-09) | 조치 |
| --- | --- | --- | --- |
| Spring Boot | **3.2.4** (2024-03) | **4.1.1** (2026-08) | 2단계로 올린다 (3절) |
| Java | **17** | 25 (LTS) / 21 (LTS) | **21** |
| Gradle | 8.7 | 9.x | Boot 4 요구사항에 맞춰 |
| jjwt | **0.11.5** | 0.13.0 | 업그레이드 또는 **제거** (5절) |
| MySQL Connector/J | `runtimeOnly` (버전 미지정, BOM 관리) | — | BOM 따라감 |
| Lombok | BOM 관리 | — | 유지 (6절 참고) |

### Spring Boot 3.2.4 를 그대로 두면

- **OSS 지원이 끝난 지 오래다.** 3.2.x는 2024-11에 OSS 지원 종료.
  이후 공개된 CVE 패치를 받지 못한다.
- Spring Security, Tomcat, Hibernate가 전부 그 시점에 묶여 있다.
- **인터넷에 공개 배포할 예정이므로 이건 선택이 아니다.**

---

## 2. 순서가 중요하다 — 테스트가 먼저다

지금 테스트가 `contextLoads()` 하나뿐이고 그마저 실행되지 않는다([11](11-testing-strategy.md)).
**이 상태에서 메이저 업그레이드를 하면 무엇이 깨졌는지 알 방법이 없다.**

```
❌ 나쁜 순서:  Boot 4 업그레이드 → 안 뜸 → 원인 불명 → 롤백
✅ 좋은 순서:  테스트 확보 → Boot 3.5 → 초록 확인 → Boot 4.1 → 초록 확인
```

[13](13-roadmap.md)에서 의존성 업그레이드를 **중반 이후**에 배치한 이유다.

---

## 3. Spring Boot 3.2.4 → 4.1.1

**두 단계로 나눈다.** 3.2 → 4.1 직행은 변경 폭이 너무 크다.

### 1단계: 3.2.4 → 3.5.x

같은 메이저 버전이라 대부분 그대로 뜬다. 여기서 deprecation 경고를 먼저 정리한다.

### 2단계: 3.5.x → 4.1.1

Spring Boot 4는 **Spring Framework 7 / Jakarta EE 11 / Servlet 6.1** 기반이다.
2.x → 3.x 때의 `javax` → `jakarta` 같은 전면 치환은 **없다.** 이번은 훨씬 수월하다.

Java 요구사항: **17 이상**. 다만 최신 LTS 사용이 권장되므로 **21로 올린다.**

이 프로젝트에서 실제로 걸리는 것:

| 변경 | 영향 | 조치 |
| --- | --- | --- |
| **스타터 이름 변경** `spring-boot-starter-web` → `spring-boot-starter-webmvc` | `build.gradle` | 이름 교체 |
| **모듈화** — 암묵적 포함이 사라짐 | `data-jpa`, `websocket` 등을 **명시적으로 선언**해야 한다 | 이미 명시되어 있어 영향 적음 |
| `@SpringBootTest`가 MockMvc를 자동 제공하지 않음 | 테스트 | `@AutoConfigureMockMvc` 추가 |
| `@MockBean` / `@SpyBean` 제거 | 테스트 | `@MockitoBean` / `@MockitoSpyBean` |
| `HttpMessageConverters` deprecated | 현재 미사용 | 영향 없음 |
| `org.springframework.lang.Nullable` 제거 | 현재 미사용 | 영향 없음 |

**Spring Security 7**과 **Hibernate 7**이 함께 올라간다. 이 프로젝트에서 확인할 지점:

- `SecurityConfig`의 람다 DSL — 3.x에서 이미 람다 방식이라 큰 변화 없음
- OAuth2 클라이언트 설정 (`kakao` 프로바이더)
- Hibernate 7의 스키마 검증이 더 엄격 → `ddl-auto: validate`로 미리 맞춰 둔다
  ([06](06-persistence-and-queries.md) 5절)

**업그레이드 도구**: OpenRewrite의 Spring Boot 4 레시피를 먼저 돌려 보면
기계적인 치환은 대부분 자동으로 처리된다.

---

## 4. Java 17 → 21

로컬에 이미 **Microsoft OpenJDK 21.0.6**이 설치되어 있다(`/usr/libexec/java_home` 확인).
빌드만 17로 고정되어 있는 상태다.

```groovy
java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}
```

`sourceCompatibility = '17'` 대신 **toolchain**을 쓴다.
로컬 JDK 버전과 무관하게 빌드가 재현된다.

### 21에서 쓸 수 있게 되는 것 — [02](02-game-rules-audit.md)의 규칙 엔진에 직접 도움이 된다

```java
// sealed interface + record + 패턴 매칭 switch
public sealed interface Move {
    record Place(int to)            implements Move {}
    record Slide(int from, int to)  implements Move {}
    record Remove(int at)           implements Move {}
}

MoveResult apply(Stone actor, Move move) {
    return switch (move) {                         // 컴파일러가 누락 분기를 잡아준다
        case Move.Place  p -> place(actor, p.to());
        case Move.Slide  s -> slide(actor, s.from(), s.to());
        case Move.Remove r -> remove(actor, r.at());
    };
}
```

지금 `MorrisService`는 `if (currentPhase == 1) ... else if (currentPhase == 2)`로
분기하고 **phase 3(플라잉)이 통째로 빠져 있다**([02](02-game-rules-audit.md) E-1).
`sealed` + `switch`로 바꾸면 **분기 누락이 컴파일 에러가 된다.**

`morris-core`는 순수 자바 모듈이므로 이 문법을 마음껏 써도 된다
([07](07-architecture-decision.md) ADR-1).

---

## 5. jjwt 0.11.5 — 업그레이드보다 제거를 먼저 검토

현재 코드는 **0.12.x에서 전부 deprecated된 API**를 쓴다.

```java
// JwtProvider.java — 구 API
Jwts.builder().setSubject(...).setIssuedAt(...).setExpiration(...)
Jwts.parserBuilder().setSigningKey(...).build().parseClaimsJws(token).getBody()
```

```java
// 0.12+ 신 API
Jwts.builder().subject(...).issuedAt(...).expiration(...)
Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()
```

### 선택지

| 옵션 | 내용 | 판정 |
| --- | --- | --- |
| **A.** jjwt 0.13.0으로 올리고 API 교체 | 30줄 수정 | ○ |
| **B.** **Spring Security OAuth2 Resource Server로 대체** | jjwt 의존성 제거 | ✅ 권장 |

**B를 권하는 이유**: `spring-boot-starter-oauth2-resource-server`를 쓰면
`JwtDecoder` / `JwtEncoder` 빈 설정만으로 끝나고, 다음이 전부 사라진다.

- `JwtProvider` (87줄) — 서명 키 관리, 검증, 클레임 추출
- `JwtAuthenticationFilter` (86줄) — 토큰 파싱, 권한 매핑, 예외 처리

Spring Security가 검증·클레임 매핑·예외 처리를 다 해준다.
[01](01-code-audit.md)의 P1-15(사용자 없으면 조용히 통과), P2-10(매 호출 키 재생성),
P2-11(정상 흐름 ERROR 로그)이 **코드가 사라지면서 함께 사라진다.**

다만 **학습 관점에서 직접 구현을 남기고 싶다면 A도 합리적이다.**
그 경우 최소한 위 세 결함은 고쳐야 한다.

---

## 6. 제거할 의존성

| 의존성 | 이유 |
| --- | --- |
| `spring-boot-starter-thymeleaf` | **템플릿이 0개다.** 프론트가 GitHub Pages로 분리되어 있다 |
| `thymeleaf-extras-springsecurity6` | 위와 동일 |
| `sockjs-client` (프론트) | `@stomp/stompjs`의 `brokerURL`로 네이티브 WebSocket을 쓴다. **SockJS를 안 쓰는데 의존성만 남아 있다** |

`src/main/resources/static/index.html`(카카오 로그인 링크 한 줄짜리 테스트 페이지)도 함께 정리한다.

## 추가할 의존성

| 의존성 | 용도 |
| --- | --- |
| `spring-boot-starter-actuator` | 헬스체크 ([10](10-logging-and-observability.md) 6절) |
| `spring-boot-starter-data-redis` | [07](07-architecture-decision.md) ADR-6 |
| `flyway-core`, `flyway-mysql` | [06](06-persistence-and-queries.md) 5절 |
| `spring-boot-testcontainers`, `mysql` (test) | [11](11-testing-strategy.md) 5절 |
| `archunit-junit5` (test) | [11](11-testing-strategy.md) 5절 |
| `spring-boot-starter-validation` | 이미 있음. 다만 `@Valid`가 안 붙어 있어 무효 ([01](01-code-audit.md) P1-12) |

---

## 7. `build.gradle` 정리

현재 파일의 문제들.

```groovy
group = 'com'                    // → 'com.ninemensmorris'
java { sourceCompatibility = '17' }   // → toolchain 21

developmentOnly 'org.springframework.boot:spring-boot-devtools'
// devtools + WebSocket 조합은 자동 재시작 시 소켓이 끊긴다. 로컬에서만 쓴다면 유지
```

- 들여쓰기가 탭이다 (다른 파일은 스페이스 4칸) — [04](04-consistency-and-naming.md) 5-3
- 버전 관리 방식이 혼재 (BOM 관리 + 하드코딩) — 하드코딩된 jjwt만 명시적이다
- **멀티모듈 전환 시 루트/서브 프로젝트로 분리**해야 한다 ([07](07-architecture-decision.md) ADR-1)

추가로 넣으면 좋은 것:

```groovy
plugins {
    id 'com.diffplug.spotless' version '...'   // 포맷 강제 ([04](04-consistency-and-naming.md) 5-3)
}
```

지금 `.editorconfig`도 Checkstyle도 없어서 **포맷을 강제할 수단이 전혀 없다.**

---

## 8. Docker 이미지

| 현재 | 문제 | 변경 |
| --- | --- | --- |
| `FROM openjdk:17-alpine` | **`openjdk` 공식 이미지는 지원 종료됐다** | `eclipse-temurin:21-jre-alpine` |
| 싱글 스테이지 | 최종 이미지에 소스·Gradle 캐시가 전부 들어감 | **멀티 스테이지** |
| `COPY ${JAR_FILE} app.jar` | 빌드 결과가 아니라 **호스트 컨텍스트**를 본다 → 깨끗한 클론에서 빌드 실패 | `COPY --from=builder` |
| `.dockerignore` 없음 | `.git`, `.gradle`이 컨텍스트로 전송 | 추가 |
| 비루트 유저 없음 | | `USER` 지정 |
| `platform: linux/amd64` (compose) | t4g는 ARM. QEMU 에뮬레이션 | 제거 또는 `linux/arm64` |

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN ./gradlew dependencies --no-daemon        # 의존성 레이어 캐싱
COPY . .
RUN ./gradlew :morris-api:bootJar --no-daemon # 테스트는 CI 에서 이미 돌았다

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=builder /build/morris-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=55", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
```

- `-Dspring.profiles.active=prod`를 `ENTRYPOINT`에 박지 않는다.
  환경변수(`SPRING_PROFILES_ACTIVE`)로 주입해야 같은 이미지를 로컬/EKS에서 재사용할 수 있다.
  현재는 하드코딩되어 있어 **로컬에서 이 이미지를 띄우면 prod 설정으로 뜬다.**
- `-Xmx` 고정값 대신 `MaxRAMPercentage` — 컨테이너 메모리 제한을 따라간다
  ([07](07-architecture-decision.md) ADR-3).

---

## 9. 진행 순서

```
1. [11](11-testing-strategy.md) 의 테스트 설정 정리 + CI 구축      ← 안전망 먼저
2. Java 17 → 21 (toolchain)                     ← 단독. 대부분 그대로 통과
3. Docker 멀티스테이지 + eclipse-temurin        ← 단독. 배포 경로 복구
4. 안 쓰는 의존성 제거 (thymeleaf 등)             ← 단독. 삭제만
5. Spring Boot 3.2.4 → 3.5.x                    ← deprecation 정리
6. Spring Boot 3.5.x → 4.1.1                    ← 스타터 이름·테스트 애너테이션
7. jjwt 처리 (제거 또는 0.13.0)                  ← 인증 구조 변경과 함께
8. Actuator / Redis / Flyway 추가                ← 기능 작업에 묶어서
```

**2~4는 서로 독립적이라 순서를 바꿔도 되고, 각각 커밋 하나로 끝난다.**
5~6이 유일하게 위험한 구간이므로 앞에 안전망을 깔아 둔다.
