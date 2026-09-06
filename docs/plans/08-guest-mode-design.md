# 08. 비로그인(게스트) 플레이 설계

목표: **로그인이 진입 장벽이 되지 않게 한다.** 카카오 로그인은 유지하되, 없이도 게임이 가능해야 한다.

---

## 1. 지금 구조로는 게스트를 만들 수 없다

```java
// user/domain/User.java:16-18
@Id
@Column(name = "user_id")
private Long userId;          // ← 카카오 회원번호가 곧 기본키다
```

**게스트에게는 카카오 회원번호가 없다.** 이 PK 구조가 게스트 도입의 유일한 진짜 장애물이다.

추가로 걸리는 곳들:

| 위치 | 문제 |
| --- | --- |
| `UserService.java:26-29` | `authentication.getName()` → 비인증이면 `"anonymousUser"` → `Long.parseLong` 실패 → **401이 아니라 500** |
| `WebSocketEventListener.java:27, 38` | `principal.getName()` — `principal`이 null이면 NPE. 게스트 도입 시 즉시 터진다 |
| `SecurityConfig.java:67-72` | 전부 `permitAll` — 게스트/회원 구분이 아니라 **구분 자체가 없다** |
| `JwtAuthenticationFilter.java:56-58` | 권한이 `"USER"` 문자열. `ROLE_` 접두사가 없어 `hasRole`이 안 먹는다 |

---

## 2. 데이터 모델

카카오 회원번호를 PK 자리에서 빼고, **인증 수단을 속성으로** 내린다.

```java
@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(name = "uk_users_provider",
                                             columnNames = {"provider", "provider_id"}))
public class User {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;                       // 서비스 내부 식별자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;             // KAKAO | GUEST

    @Column(length = 64)
    private String providerId;             // 카카오 회원번호. 게스트는 null

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;                     // ROLE_GUEST | ROLE_USER

    @Column(nullable = false)
    private int rating;                    // [09](09-ranking-design.md)

    private Instant createdAt;
    private Instant lastSeenAt;            // 유휴 게스트 정리 기준

    public boolean isGuest() { return provider == Provider.GUEST; }

    public static User guest(String nickname) {
        User u = new User();
        u.provider = Provider.GUEST;
        u.providerId = null;
        u.nickname = nickname;
        u.role = Role.ROLE_GUEST;
        u.rating = Rating.INITIAL;
        u.createdAt = u.lastSeenAt = Instant.now();
        return u;
    }

    public static User ofKakao(String kakaoId, String nickname, String imageUrl) { ... }
}
```

- `role`을 **`ROLE_GUEST` / `ROLE_USER`로 저장**한다.
  `ROLE_` 접두사를 붙여야 `hasRole()`이 동작한다 — [01](01-code-audit.md) P1-2를 여기서 함께 고친다.
- `nickname`에 유니크 제약을 건다. 게스트 닉네임 자동 생성 시에도 충돌을 DB가 막아준다.
- `email` 필드는 **제거한다.** 게스트에게는 없고, 카카오도 동의 항목이라 null일 수 있으며,
  현재 코드에서 쓰이는 곳이 `findByEmail`(호출처 없음) 하나뿐이다.

---

## 3. 게스트 발급

```
POST /api/v1/auth/guests
→ 201 Created
{
  "accessToken": "eyJhbG...",
  "user": { "id": 1042, "nickname": "조용한물맷돌4821", "role": "ROLE_GUEST", "guest": true }
}
```

- 서버가 `users` 행을 만들고 JWT를 발급한다. 클라이언트 입력은 **없다**(닉네임도 서버가 정한다).
- JWT 클레임: `sub=id`, `role=ROLE_GUEST`, `guest=true`.
  `guest` 클레임이 있으면 필터가 DB를 안 쳐도 게스트 여부를 안다
  ([06](06-persistence-and-queries.md) 3-4).
- 토큰은 `Authorization: Bearer` 헤더로 주고받는다 — [07](07-architecture-decision.md) ADR-5.

### 닉네임 생성

`형용사 + 명사 + 4자리 숫자` 조합 (예: `조용한물맷돌4821`).
유니크 제약 위반 시 최대 5회 재시도 후 실패 처리.
게스트가 닉네임을 직접 정하게 할 수도 있지만, **그 순간 진입 장벽이 다시 생긴다.**
자동 생성 후 원하면 바꾸게 하는 편이 목적에 맞다.

### 남용 방지

게스트 발급은 **인증 없이 호출되는 쓰기 엔드포인트**다. 막지 않으면 DB에 행이 무한 생성된다.

```java
// Redis — [07](07-architecture-decision.md) ADR-6 ④
INCR ratelimit:guest:{ip}      // 첫 호출 시 EXPIRE 3600
// 시간당 10회 초과 → 429 Too Many Requests
```

CAPTCHA는 이 규모에 과하다. IP 레이트 리밋 + 정리 배치(6절)면 충분하다.

---

## 4. 게스트가 할 수 있는 것과 없는 것

| 기능 | 게스트 | 회원 |
| --- | --- | --- |
| 방 목록 조회 | ✅ | ✅ |
| 방 생성 / 참가 / 게임 플레이 | ✅ | ✅ |
| 랭킹 **조회** | ✅ | ✅ |
| **랭킹 등재 (레이팅 변동)** | ❌ | ✅ |
| 전적 기록 (`matches`) | ❌ | ✅ |
| 닉네임 변경 | ✅ (임시) | ✅ |
| 프로필 이미지 | 기본 아바타 | 카카오 프로필 |

### 게스트가 낀 게임은 "일반전"으로 처리한다 — 양쪽 다 레이팅 무변동

이게 이 설계에서 가장 중요한 결정이다.

**이유**: 게스트는 무제한으로 만들 수 있다.
게스트 대 회원 게임에서 회원의 레이팅이 오른다면,
**게스트 계정을 여러 개 만들어 자기 본계정에 점수를 몰아주는 것**이 가능하다.
게스트에게만 레이팅을 안 주는 것으로는 막을 수 없다 — 회원 쪽이 이득을 보기 때문이다.

```
회원 vs 회원   →  랭크전.  Elo 변동 O, matches 기록 O
게스트가 낀 게임 →  일반전.  Elo 변동 X, matches 기록 X
```

**부수 효과가 좋다.** "랭킹에 오르려면 로그인" 이라는 자연스러운 로그인 유인이 생긴다.
로그인을 강제하지 않으면서도 로그인할 이유를 남긴다.

UI에서는 방 목록과 게임 화면에 **`랭크전` / `일반전` 배지**를 표시한다.
방에 게스트가 들어오는 순간 랭크전이 일반전으로 바뀌므로, 그 전환을 화면에 보여줘야 한다.

---

## 5. 게스트 → 회원 전환

게스트로 놀다가 카카오 로그인을 누른 경우.

```
게스트 토큰 보유 상태에서 카카오 로그인 성공
  → 카카오 계정으로 기존 회원이 있으면    : 그 계정으로 로그인. 게스트 행은 정리 대상으로 표시
  → 없으면                              : 신규 회원 생성. 게스트 행은 정리 대상으로 표시
```

**게스트 데이터를 회원 계정으로 이관하지 않는다.**
게스트는 레이팅도 전적도 없으므로 옮길 게 없다.
닉네임만 "쓰던 닉네임을 유지할까요?" 정도로 물어볼 수 있으나, 없어도 된다.

- **게임 진행 중에는 로그인 버튼을 막는다.** 세션 주체가 바뀌면 그 게임을 이어갈 수 없다.
- 서버에서도 방어한다: 게임 참여 중인 사용자의 OAuth 콜백은 거절하거나, 방에서 먼저 내보낸다.

---

## 6. 게스트 수명과 정리

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 게스트 액세스 토큰 TTL | **7일** | 짧으면 게임 도중 만료된다. 게스트는 탈취 시 피해가 거의 없어 길게 잡아도 된다 |
| 게스트 행 보존 | **마지막 접속 후 30일** | `last_seen_at` 기준 |
| 정리 주기 | 매일 새벽 1회 (`@Scheduled`) | |

```java
@Scheduled(cron = "0 0 4 * * *")
@Transactional
public void purgeIdleGuests() {
    int deleted = userRepository.deleteGuestsLastSeenBefore(Instant.now().minus(30, DAYS));
    if (deleted > 0) log.info("유휴 게스트 정리 완료. 삭제 {}건", deleted);
}
```

**브라우저가 토큰을 잃으면(캐시 삭제, 다른 기기) 새 게스트 행이 생긴다.**
이건 게스트 방식의 본질적 특성이므로 막을 수 없고, 정리 배치로 관리한다.
`users(last_seen_at)` 인덱스가 필요한 이유다([06](06-persistence-and-queries.md) 4절).

---

## 7. 인가 규칙 — 게스트를 열면서 오히려 조인다

현재는 `anyRequest().permitAll()`이라 **모든 엔드포인트가 무방비**다([01](01-code-audit.md) P1-1).
게스트 도입은 "더 열자"가 아니라 **"게스트에게도 신원을 주고, 그 위에서 잠그자"** 는 작업이다.

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/oauth2/**", "/api/oauth2/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/auth/guests").permitAll()
    .requestMatchers(HttpMethod.GET,  "/api/v1/rankings").permitAll()
    .requestMatchers("/api/v1/**").hasAnyRole("GUEST", "USER")   // 게스트도 신원이 있다
    .anyRequest().denyAll()                                       // 기본값을 거부로
);
```

- `hasRole("GUEST")`가 동작하려면 권한이 `ROLE_GUEST`로 저장되어야 한다(2절).
- `.anyRequest().denyAll()` — 새 엔드포인트를 추가하면 **명시적으로 열어야만** 동작한다.
  `permitAll()`이 기본이면 실수로 열린 엔드포인트를 못 잡는다.
- `AuthenticationEntryPoint`는 **401**을 반환하도록 고친다(현재 403, [01](01-code-audit.md) P1-8).

---

## 8. WebSocket

현재 `WebSocketEventListener`는 `Principal`이 반드시 있다고 가정한다([01](01-code-audit.md) P1-14).
게스트도 JWT를 갖게 되므로 신원 자체는 생기지만, **인증 지점을 명시화해야 한다.**

```java
// STOMP CONNECT 프레임에서 인증 — 쿠키에 의존하지 않는다
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String bearer = accessor.getFirstNativeHeader("Authorization");
                accessor.setUser(jwtAuthenticator.authenticate(bearer));  // 실패 시 예외 → 연결 거부
            }
            return message;
        }
    });
}
```

- 인증 실패 시 **연결을 거부**한다. 그러면 `Principal`이 null인 경우가 아예 없어진다.
- 프론트는 `connectHeaders`로 토큰을 보낸다.

```ts
new Client({
  brokerURL: import.meta.env.VITE_SOCKET_URL,
  connectHeaders: { Authorization: `Bearer ${token}` },   // 현재 없음
});
```

- 같은 인터셉터에서 [01](01-code-audit.md) P0-12(클라이언트의 `/topic` 직접 발행)도 함께 막는다.

---

## 9. 프론트엔드 변경

| 항목 | 현재 | 변경 |
| --- | --- | --- |
| 랜딩 화면 | 카카오 로그인 버튼만 | **"게스트로 바로 시작"** 버튼 추가 (기본 CTA) |
| 토큰 | `withCredentials: true` 쿠키 | `Authorization` 헤더 (`src/lib/api.ts:5`) |
| `getCurrentUser` 401 처리 | `throw new Error('로그아웃된 사용자입니다.')` | 게스트 자동 발급 후 재시도 |
| STOMP 연결 | `connectHeaders` 없음 | 토큰 전달 (`src/pages/Game/index.tsx:31-36`) |
| 게임/방 화면 | 구분 없음 | `랭크전` / `일반전` 배지 |

**"게스트로 시작"을 기본 버튼으로 두고 카카오 로그인을 보조로 배치**하는 것이
"로그인이 진입 장벽"이라는 문제의식에 맞다.

---

## 10. 작업 순서

게스트는 **마지막에 붙이는 기능**이다. 앞의 정리가 안 되면 붙일 곳이 없다.

```
1. User PK 교체 (provider / providerId 도입)      ← [06](06-persistence-and-queries.md) 5절
2. Role 을 ROLE_ 접두사로 저장                     ← P1-2 해결
3. 토큰을 쿠키 → 헤더로 전환                        ← [07](07-architecture-decision.md) ADR-5
4. STOMP CONNECT 인증 인터셉터                     ← P1-14, P0-12 동시 해결
5. 서비스에서 SecurityContextHolder 직접 참조 제거   ← [03](03-layering-and-dto.md) ❷
6. ───── 여기까지 되면 게스트는 아래 3개로 끝난다 ─────
7. POST /api/v1/auth/guests + 닉네임 생성기 + 레이트 리밋
8. 인가 규칙 전환 (permitAll → denyAll 기본)
9. 랭크전/일반전 분기 + 유휴 게스트 정리 배치
```

**1~5는 게스트 때문이 아니라 어차피 해야 하는 일**이고, 그게 끝나면 게스트 자체는 작은 작업이다.
반대로 지금 상태에서 게스트만 먼저 넣으면
`SecurityContextHolder`가 `"anonymousUser"`를 뱉는 지점마다 500이 난다.
