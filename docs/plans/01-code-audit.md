# 01. 코드 전수조사

심각도: **P0** 즉시 수정 / **P1** 재배포 전 수정 / **P2** 개선 권장 / **P3** 취향·정리

---

## P0 — 게임이 틀리게 동작하거나 악용 가능

### P0-1. 승패 점수 가감이 뒤집혀 있다
`game/service/MorrisService.java:284-290`

```java
if (winnerId.equals(gameRoom.getPlayerOneId())) {
    userService.increaseScore(winnerId, 30);
    userService.decreaseScore(loserId, 20);
} else {
    userService.increaseScore(loserId, 30);   // ← 패자에게 +30
    userService.decreaseScore(winnerId, 20);  // ← 승자에게 -20
}
```

게스트(playerTwo)가 이기면 **진 사람이 30점을 얻고 이긴 사람이 20점을 잃는다.**
`else` 분기에서 이미 `winnerId`가 승자인데 다시 뒤집었다. 분기 자체가 불필요하다.

```java
userService.increaseScore(winnerId, 30);
userService.decreaseScore(loserId, 20);
```

### P0-2. 기권 요청의 `userId`를 클라이언트가 정한다
`game/dto/Morris/WithdrawRequestDto.java:8-9`, `game/service/MorrisService.java:317-327`

```java
Long userId = requestDto.getUserId();            // 클라이언트 payload
Long winnerId = gameRoom.getPlayerOneId().equals(userId)
        ? gameRoom.getPlayerTwoId() : gameRoom.getPlayerOneId();
userService.increaseScore(winnerId, 30);
```

**상대방의 userId를 넣어서 보내면 상대가 기권한 것으로 처리되고 내가 +30을 받는다.**
인증 주체(`Principal`)를 쓰지 않고 payload를 신뢰한 결과. 한 줄이면 승률을 조작할 수 있다.
`TieRequestDto.userId`도 같은 구조다(현재는 미사용이라 무해).

### P0-3. 착수 검증이 전무하다 — 서버가 규칙을 강제하지 않는다
`game/service/MorrisService.java:107-188, 367-377`

`placeStone` / `placeStonePhaseOne` / `placeStonePhaseTwo` 어디에도 다음 검증이 없다.

| 검증 항목 | 현재 |
| --- | --- |
| 요청자가 현재 턴인가 | ❌ 없음 (`currentTurns`와 대조 안 함) |
| 요청자가 이 게임의 플레이어인가 | ❌ 없음 |
| 좌표가 0~23 범위인가 | ❌ 없음 → `ArrayIndexOutOfBoundsException` |
| (1단계) 목적지가 빈 칸인가 | ❌ 없음 → 상대 돌 위에 덮어쓰기 가능 |
| (2단계) 옮기려는 돌이 내 돌인가 | ❌ 없음 → 상대 돌을 내가 이동시킬 수 있음 |
| (2단계) 출발지가 빈 칸이 아닌가 | ❌ 없음 → `"EMPTY"` 문자열을 목적지에 복사 |
| (2단계) 인접한 칸인가 | ❌ 없음 → 반대편으로 순간이동 가능 |

```java
private void placeStonePhaseTwo(Long gameId, int initialPosition, int finalPosition) {
    String[] board = gameBoards.get(gameId);
    String stone = board[initialPosition];   // 검증 0
    board[initialPosition] = EMPTY_CELL;
    board[finalPosition] = stone;
}
```

즉 **규칙 강제가 전부 프론트엔드에 있다.** STOMP 프레임을 직접 만들면 아무 수나 둘 수 있다.
규칙 위반 상세는 [02-game-rules-audit.md](02-game-rules-audit.md) 참고.

### P0-4. 돌 제거도 검증이 없다
`game/service/MorrisService.java:190-249`

- 방금 밀(mill)을 만들었는지 확인하지 않는다 → 아무 때나 `/app/game/removeOpponentStone` 호출 가능
- 제거 대상이 **상대 돌인지 확인하지 않는다** → 내 돌을 지워도 통과하고, 그러면서 **상대 돌 카운트가 줄어든다**

```java
board[removePosition] = EMPTY_CELL;               // 소유자 확인 없음
if (currentPlayerStone.equals(PLAYER_ONE_STONE)) {
    guestTotal.put(gameId, guestTotal.get(gameId) - 1);   // 무조건 상대 카운트 차감
}
```

내 돌을 9번 지우면 상대 `guestTotal`이 0이 되어 내가 이긴다.

### P0-5. 제거 실패 응답이 `isRemoving=false`를 보내 게임이 멈춘다
`game/service/MorrisService.java:220-237`

밀에 속한 돌을 제거하려다 거절될 때(`CANNOT_REMOVE_ROW_COLUMN`) 응답의 `isRemoving`이 `false`다.
서버는 턴을 넘기지 않았는데 클라이언트는 "제거 단계 종료"로 해석한다 → **양쪽 상태가 어긋나 게임이 진행 불가.**
이 응답은 `isRemoving(true)`여야 한다.

### P0-6. 소켓 끊김 처리에서 NPE
`game/service/MorrisService.java:45-54`

```java
GameRoom gameRoom = findRoomByUserId(userId);
if (gameRoom != null) { ... }                                  // null 가드
simpMessagingTemplate.convertAndSend("/topic/game/" + gameRoom.getId(), "SOCKET_ERROR");  // 가드 밖
```

`gameRoom`이 null이면 마지막 줄에서 NPE. 그리고 `gameRooms` 맵은 `startGame()`에서만 채워지므로
**게임 시작 전 대기실에서 나간 사용자는 항상 null** → 끊길 때마다 NPE가 난다.
동시에 그 사용자의 `GameRoom` 행은 MySQL에 영구히 남는다(고아 방).

### P0-7. `findRoomByUserId`에서 NPE
`game/service/MorrisService.java:63-70`

```java
if (room.getPlayerOneId().equals(userId) || room.getPlayerTwoId().equals(userId))
```

`playerTwoId`는 nullable인데 그 위에서 `.equals()`를 호출한다. 1인 대기 방이 하나라도 있으면 터진다.
`Objects.equals(...)` 또는 `userId.equals(room.getPlayerTwoId())` 로 뒤집어야 한다.

### P0-8. 게임 상태가 동기화 없는 `HashMap` 11개
`game/service/MorrisService.java:34-43`

```java
private final Map<Long, String[]>  gameBoards   = new HashMap<>();
private final Map<Long, Integer>   gamePhases   = new HashMap<>();
... (총 11개)
```

- STOMP 메시지는 `clientInboundChannel` 스레드 풀(기본 `availableProcessors`)에서 **병렬 처리**된다.
  싱글턴 빈의 `HashMap`을 락 없이 동시 `put`/`get`/`remove` → 값 유실, 최악의 경우 리사이즈 중 무한루프.
- **게임 종료 시 어떤 맵에서도 항목을 지우지 않는다** → 게임을 할수록 메모리가 단조 증가(누수).
- 상태가 11개 맵에 흩어져 있어 원자적으로 갱신할 방법이 없다. `gameId` 하나에 대한 상태는
  **객체 하나**로 묶여야 한다.

### P0-9. `startGame`을 누구나 호출할 수 있다
`game/controller/MorrisController.java:33-37`

```java
@MessageMapping("/game/startGame")
public void startGame(Long gameId) { ... }
```

인증·소속 확인이 없다. 진행 중인 아무 게임 id나 넣어 보내면 **판이 초기화된다.**

### P0-10. 게임 참가 시 `userId`를 클라이언트가 정한다
`game/controller/GameRoomController.java:34-42`

```java
public ResponseEntity<String> joinGame(@PathVariable Long roomId, @RequestBody Long userId)
```

남의 계정으로 방에 입장시킬 수 있다. `Principal`을 써야 한다. STOMP 쪽(`MorrisController:24-31`)도 동일.

### P0-11. 방 입장에 경합 조건이 있다
`game/service/GameRoomService.java:81-97`

`playerTwoId == null` 확인 후 `save()` — 두 요청이 동시에 들어오면 둘 다 통과하고 나중 것이 이긴다.
낙관적 락(`@Version`) 또는 조건부 UPDATE(`update ... where player_two_id is null`)가 필요하다.

### P0-12. 클라이언트가 `/topic`으로 직접 메시지를 발행할 수 있다
`config/WebSocketConfig.java:20-23`

```java
registry.enableSimpleBroker("/queue", "/topic");   // server -> client
registry.setApplicationDestinationPrefixes("/app"); // client -> server
```

주석은 `/topic`이 서버→클라 전용이라고 말하지만, **Spring의 SimpleBroker는
클라이언트가 `/topic/**`으로 보낸 `SEND` 프레임도 그대로 구독자에게 중계한다.**
`/app` 접두사는 "여기로 보내면 `@MessageMapping`이 받는다"는 뜻일 뿐,
다른 곳으로 못 보낸다는 뜻이 아니다.

프론트엔드가 실제로 이 경로를 쓰고 있다 (`NineMensMorris_FrontEnd/src/hooks/useGameState.ts`).

```js
client.publish({
  destination: `/topic/game/${roomId}`,        // ← 서버를 거치지 않고 상대에게 직접
  body: JSON.stringify({ type: 'CLIENT_EVENT', contents: `ADD_BLACK` }),
});
```

효과음 동기화 용도로 넣은 것으로 보이지만, **아무 메시지나 보낼 수 있다는 뜻이다.**
상대 클라이언트의 수신 처리는 이렇게 되어 있다
(`NineMensMorris_FrontEnd/src/pages/Game/index.tsx:146-180`).

```js
switch (response.type) {
  case 'GAME_OVER':
  case 'GAME_WITHDRAW':
    setGameState(response.data);
    setShowGameResultModal(true);       // 위조 결과창이 그대로 뜬다
    break;
  default:
    setGameState(response.data);        // 임의의 보드 상태를 주입할 수 있다
}
```

즉 브라우저 콘솔에서 한 줄이면 **상대 화면에 위조된 게임 종료·위조된 판**을 띄울 수 있다.
서버가 계산한 점수까지 바뀌지는 않지만, P0-2·P0-4와 조합하면 그쪽도 조작된다.

**대응**: `/topic`·`/queue`로의 클라이언트 `SEND`를 차단한다.

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            var accessor = StompHeaderAccessor.wrap(message);
            if (StompCommand.SEND.equals(accessor.getCommand())
                    && !accessor.getDestination().startsWith("/app/")) {
                throw new AccessDeniedException("클라이언트는 /app 으로만 전송할 수 있다");
            }
            return message;
        }
    });
}
```

효과음 동기화는 서버를 경유하도록 바꾼다 —
어차피 착수 결과를 서버가 브로드캐스트하므로 **별도 메시지가 필요 없다.**

### P0-13. 서버가 보내는 브로드캐스트 하나는 아무도 안 듣는다
`game/controller/MorrisController.java:30`

```java
simpMessagingTemplate.convertAndSend("/topic/gameRoom/" + roomId, roomId + "번 게임 방에 참가했습니다.");
```

프론트엔드는 `/topic/game/${roomId}` **하나만** 구독한다
(`NineMensMorris_FrontEnd/src/pages/Game/index.tsx:208`).
`/topic/gameRoom/**`을 구독하는 코드가 없다 → 이 메시지는 어디에도 도달하지 않는다.
토픽을 하나로 합치면서 함께 정리한다([05](05-api-and-protocol.md)).

---

## P1 — 보안 / 인증 / 배포 시 반드시 걸림

### P1-1. 모든 엔드포인트가 열려 있다
`config/SecurityConfig.java:67-72`

```java
.requestMatchers("/", "/api/oauth2/**").permitAll()
//  .requestMatchers("/api/user/**").hasRole("USER")     ← 주석 처리됨
.anyRequest().permitAll()
```

인가 규칙이 없다. 아래 P1-2가 원인으로 보인다.

### P1-2. 권한 문자열에 `ROLE_` 접두사가 없어 `hasRole`이 영원히 실패한다
`security/filter/JwtAuthenticationFilter.java:56-58`, `user/domain/User.java:23`

DB에는 `"USER"`가 저장되고 `new SimpleGrantedAuthority("USER")`로 등록된다.
그런데 `hasRole("USER")`는 내부적으로 `ROLE_USER`를 찾는다 → 절대 매칭되지 않는다.
**P1-1에서 인가 규칙을 주석 처리한 진짜 이유가 이것이다.**
`hasAuthority("USER")`로 바꾸거나, 권한을 `ROLE_USER`로 저장해야 한다.

### P1-3. 액세스 토큰 쿠키 수명이 JWT의 1000배
`security/handler/OAuth2SuccessHandler.java:40`

```java
accessTokenCookie.setMaxAge(Math.toIntExact(accessTokenExpiration));
```

`ACCESS_TOKEN_EXPIRATION`은 밀리초 단위다(`JwtProvider:43` 에서 `now.getTime() + expirationPeriod`).
`Cookie.setMaxAge`는 **초** 단위다. 1시간(3,600,000ms) 설정 시 쿠키는 **약 41일** 유지된다.
JWT가 만료된 뒤에도 브라우저는 죽은 쿠키를 계속 보내고, 서버는 매 요청 `log.error`를 찍는다.

### P1-4. 쿠키에 `Secure` / `SameSite`가 없다
`security/handler/OAuth2SuccessHandler.java:38-43`

```java
//accessTokenCookie.setSecure(true); // 240404 ldhbenecia | https 설정 이후 사용
```

**GitHub Pages 배포 시 이건 치명적이다.** `*.github.io` ↔ AWS 도메인은 크로스사이트라
`SameSite=None; Secure` 없이는 쿠키가 아예 전송되지 않는다. 게다가 Safari는
`SameSite=None` 서드파티 쿠키를 차단하므로 **설정해도 Safari에서 로그인이 안 된다.**
대응 방안은 [07-architecture-decision.md](07-architecture-decision.md) "배포 토폴로지" 절 참고.
구현은 `jakarta.servlet.http.Cookie` 대신 `ResponseCookie`를 쓰면 `SameSite` 지정이 가능하다.

### P1-5. 액세스 토큰을 응답 헤더로도 내보낸다
`security/handler/OAuth2SuccessHandler.java:45`

```java
response.addHeader("Authorization", "Bearer " + accessToken);
```

이 응답은 302 리다이렉트다. HttpOnly 쿠키로 이미 전달했는데 헤더로 중복 노출할 이유가 없다.
프록시/로그에 남는다. 삭제 권장.

### P1-6. 리프레시 토큰이 만들어지지만 아무도 안 쓴다
`security/provider/JwtProvider.java:57-59`

`generateRefreshToken`은 호출되는 곳이 없다. 즉 **토큰 갱신도, 폐기도 없다.**
로그아웃해도 발급된 JWT는 만료 전까지 유효하다.

### P1-7. 로그아웃이 실질적으로 동작하지 않는다
`config/SecurityConfig.java:54-62`, `auth/service/LogoutService.java:11-12`

```java
public void logout(HttpServletRequest r, HttpServletResponse s, Authentication a) {}   // 빈 몸통
```

- `LogoutService`는 아무것도 하지 않는 빈 구현체인데 빈으로 주입되어 있다.
- `logoutSuccessHandler`를 지정하면 `logoutSuccessUrl("https://www.ninemensmorris.site")`은 **무시된다.**
  두 설정이 충돌한다.
- `deleteCookies("access_token")`은 `Path`/`Domain`이 발급 시와 일치해야 지워진다. 명시가 없어 불확실.

### P1-8. 인증 실패에 401이 아니라 403을 준다
`config/SecurityConfig.java:101-113`

`AuthenticationEntryPoint`는 "인증되지 않음" 상황이므로 **401 Unauthorized**가 맞다.
403은 "인증됐지만 권한 없음"(`AccessDeniedHandler`)이다. 프론트에서 재로그인 유도 분기가 꼬인다.
덤으로 이 클래스는 `SecurityConfig.java` 파일 하단에 패키지 프라이빗으로 얹혀 있다 → 별도 파일로 분리.

### P1-9. WebSocket이 모든 오리진을 허용한다
`config/WebSocketConfig.java:15-17`

```java
registry.addEndpoint("/morris-websocket").setAllowedOriginPatterns("*");
```

쿠키 기반 인증 + 와일드카드 오리진 = **CSWSH(Cross-Site WebSocket Hijacking)**.
악성 사이트가 방문자의 쿠키로 소켓을 열어 게임을 조작할 수 있다. 오리진을 명시해야 한다.

### P1-10. CORS 허용 목록에 실제 사용할 오리진이 없다
`config/SecurityConfig.java:89-90`

```java
configuration.addAllowedOrigin("http://localhost:5173");
configuration.addAllowedOrigin("https://ninemensmorris.site");
```

- 로그아웃은 `https://www.ninemensmorris.site`로 리다이렉트하는데 `www`는 허용 목록에 없다.
- **GitHub Pages로 옮기면 `https://<user>.github.io`를 추가해야 한다.**
- 값이 하드코딩되어 있다 → `application.yml` 프로퍼티로 빼야 한다.

### P1-11. 회원가입 응답에 비밀번호 해시를 담아 반환한다
`auth/service/AuthService.java:38-45`, `auth/dto/SignUpResponseDto.java:13`

```java
return SignUpResponseDto.builder()
        .password(savedUser.getPassword())   // bcrypt 해시를 그대로 응답 본문에
```

응답 DTO에 `password` 필드가 있을 이유가 없다.

### P1-12. `@Valid`가 없어 검증 애너테이션이 전부 무효
`auth/controller/AuthController.java:20`

```java
public ResponseEntity<SignUpResponseDto> signUp(@RequestBody SignUpRequestDto requestDto)
```

`SignUpRequestDto`의 `@NotBlank`, `@Pattern`은 **한 번도 실행되지 않는다.**
`@Valid`를 붙여야 하고, `MethodArgumentNotValidException` 핸들러도 추가해야 한다(P2-6).

### P1-13. 자체 로그인이 반쯤 만들어진 채로 방치되어 있다
`auth/**`, `user/**`

- `MorrisUser` 엔티티 + `MorrisUserRepository` + `AuthService.signUp` + `POST /api/signup`은 있는데
  **로그인 엔드포인트가 없다.** `MorrisUserRepository`에 `findByLoginId`조차 없다.
- `loginId`에 유니크 제약이 없다 → 같은 아이디로 무한 가입 가능.
- `MorrisUser`와 `User`가 **완전히 분리된 두 개의 사용자 테이블**이다. 게임/랭킹은 `User`만 쓴다.
  즉 자체 가입한 사용자는 게임을 할 수 없다.
- 커밋 `15003e9 feat: 자체 로그인 초안 설계 구현` 이후 진전 없음.

**판단: 이 코드는 삭제 대상이다.** 게스트 모드 설계([08](08-guest-mode-design.md))가 이 자리를 대체한다.

### P1-14. 소켓 이벤트 리스너에서 무방비 NPE
`config/WebSocketEventListener.java:27, 38`

```java
Principal principal = accessor.getUser();
String userId = principal.getName();          // null 체크 없음
morrisService.addSocket(Long.parseLong(userId), sessionId);   // 숫자 아니면 예외
```

비로그인 사용자가 소켓을 열면 `principal`이 null이다. **게스트 모드를 넣으면 즉시 터지는 지점**이다.
현재는 핸드셰이크 HTTP 요청에 걸린 서블릿 필터가 `SecurityContext`를 채워준 덕에 우연히 동작한다.
STOMP `CONNECT` 프레임에서 인증하는 `ChannelInterceptor`로 명시화해야 한다.

### P1-15. JWT 필터가 매 요청 DB를 조회하고, 사용자가 없으면 조용히 통과시킨다
`security/filter/JwtAuthenticationFilter.java:53-71`

```java
User user = userRepository.findByUserId(userId);
String role = user.getRole();          // user == null 이면 NPE
...
} catch (Exception exception) {
    log.error("Failed to process JWT token", exception);   // 삼키고
}
filterChain.doFilter(request, response);                   // 그대로 진행
```

- 탈퇴/삭제된 사용자의 토큰 → NPE → catch에서 삼킴 → **비인증 상태로 요청이 계속 진행된다.**
- `role`만 얻자고 모든 요청마다 SELECT 1회. JWT 클레임에 넣으면 0회가 된다.

### P1-16. `CustomOAuth2User`가 `OAuth2User` 계약을 위반한다
`user/domain/CustomOAuth2User.java:18-26`

```java
public Map<String, Object> getAttributes() { return null; }
public Collection<? extends GrantedAuthority> getAuthorities() { return null; }
```

인터페이스 계약상 빈 컬렉션이어야 한다. Spring Security 내부에서 순회하면 NPE가 난다.
현재는 성공 핸들러가 `getName()`만 쓰기 때문에 운 좋게 안 터진다.

### P1-17. OAuth 프로바이더 판별이 표시 이름 기준이다
`auth/service/CustomOAuth2UserService.java:27, 37`

```java
String oauthClientName = userRequest.getClientRegistration().getClientName();
if (oauthClientName.equals("kakao")) { ... }
```

`getClientName()`은 **사람이 읽는 표시 이름**이라 설정에서 바뀔 수 있다. `getRegistrationId()`가 맞다.
매칭 실패 시 `userId`가 null로 남아 `new CustomOAuth2User(null)` → `getName()`이 문자열 `"null"` →
`Long.parseLong("null")`에서 폭발한다. `else`에 예외를 던져야 한다.

---

## P2 — 정확성·성능·운영

### P2-1. 점수 갱신에 트랜잭션도 락도 없다
`user/service/UserService.java:55-65`

```java
public void increaseScore(Long userId, int score) {
    User user = userRepository.findByUserId(userId);
    user.setScore(user.getScore() + score);
    userRepository.save(user);
}
```

- `UserService`에 `@Transactional`이 전혀 없다. 승자 +30이 커밋되고 패자 -20이 실패할 수 있다.
- read-modify-write라 동시 갱신 시 갱신 손실(lost update).
- 점수가 **음수로 내려간다** (0점에서 -20 → -20점).
- → 단일 UPDATE 쿼리로 바꾸고 하한을 두어야 한다. [06](06-persistence-and-queries.md) 참고.

### P2-2. 랭킹 조회가 전체 테이블 스캔이다
`user/service/UserService.java:41-48`

```java
List<User> users = em.createQuery("SELECT u FROM User u ORDER BY u.score DESC", User.class)
        .getResultList();
```

LIMIT 없음, 페이징 없음, `score` 인덱스 없음, 필요 없는 `email`/`role`까지 전부 로딩.
사용자가 늘면 그대로 장애가 된다. `EntityManager` 직접 주입도 불필요하다.

### P2-3. 방 생성이 같은 사용자를 두 번 조회한다
`game/service/GameRoomService.java:30-37, 58-69`

```java
String currentNickname = currentUserNickname();          // findByUserId 로 User 조회
User hostUser = userRepository.findByNickname(currentNickname);   // 같은 User 를 다시 조회
```

쿼리 2회. 게다가 **닉네임에 유니크 제약이 없어** 동명이인이 있으면 엉뚱한 사용자가 반환된다.
`findByUserId` 한 번이면 끝난다.

### P2-4. `checkEndGameConditions`와 `determineWinner`가 완전히 동일한 로직을 두 번 계산한다
`game/service/MorrisService.java:475-508`

두 메서드의 본문이 조건문까지 같다. 종료 판정을 두 번 돌리고 결과를 두 번 해석한다.
**승자를 담은 `Optional<Long>` 하나를 반환하는 메서드 하나**로 합쳐야 한다.

### P2-5. 종료 판정이 1단계(배치 단계)에도 적용된다
`game/service/MorrisService.java:251, 475-489`

`removeOpponentStone`은 단계와 무관하게 `checkEndGameConditions`를 호출한다.
그런데 "움직일 수 없으면 패배"는 **2단계 이후에만 성립하는 규칙**이다.
1단계에는 손에 든 돌을 놓으면 되므로 보드에서 못 움직여도 패배가 아니다.
현재 보드 크기 덕에 실제로 오판정이 나기 어렵지만 논리적으로 틀렸다.

### P2-6. 예외 처리 범위가 좁다
`common/exception/CustomExceptionHandler.java`

- `CustomException` 하나만 처리한다. `MethodArgumentNotValidException`, 그 외 `Exception` 폴백 없음.
- `ErrorCode.SYSTEM_EXCEPTION`, `NOT_FOUND_HANDLER`는 선언만 되고 쓰이지 않는다.
- **`@RestControllerAdvice`는 `@MessageMapping`에 적용되지 않는다.** STOMP 핸들러에서 던진
  `CustomException`은 클라이언트에 전달되지 않고 로그로만 남는다.
  `@MessageExceptionHandler` + `@SendToUser("/queue/errors")`가 필요하다.
  (`ErrorCode.NOT_FOUND_SESSION_ID`가 미사용인 것도 같은 이유)

### P2-7. 재접속 동기화가 없다
`common/response/MorrisResponse.java:31`

`ResponseType.SYNC_GAME`이 정의만 되어 있고 쓰이는 곳이 없다.
새로고침하면 판을 잃는다. `GET /api/games/{id}/state` 또는 재접속 시 스냅샷 전송이 필요하다.

### P2-8. 소켓 끊김 응답만 타입이 다르다
`game/service/MorrisService.java:53`

```java
simpMessagingTemplate.convertAndSend("/topic/game/" + id, "SOCKET_ERROR");   // 평문 문자열
```

다른 모든 메시지는 `MorrisResponse<T>` JSON이다. `ResponseType.ERROR`가 이미 있는데 안 쓴다.
`MorrisController.joinGame:30`도 `"3번 게임 방에 참가했습니다."` 라는 한국어 평문을 보낸다.
같은 토픽에 서로 다른 스키마가 흐르면 프론트가 방어 코드를 써야 한다.

### P2-9. 방 목록에 페이징이 없고, 죽은 방이 쌓인다
`game/service/GameRoomService.java:47-55`

`findAll()` 전체 반환. `GameRoom`에 `status`도 `createdAt`도 없어서
서버가 죽거나 P0-6 경로를 타면 생긴 고아 방을 걸러낼 수단이 없다.

### P2-10. `getSigningKey()`가 호출마다 BASE64 디코딩을 한다
`security/provider/JwtProvider.java:36-39`

요청마다 키를 새로 만든다. `@PostConstruct`에서 한 번 만들어 필드로 들고 있으면 된다.

### P2-11. 정상 상황에서 `log.error`가 남는다
`security/filter/JwtAuthenticationFilter.java:48`, `security/provider/JwtProvider.java:72`

만료된 쿠키를 가진 사용자가 접속할 때마다 ERROR 로그. 정상 흐름이므로 `debug`가 맞다.
P1-3 때문에 이 상황이 매우 흔하게 발생한다.

### P2-12. Dockerfile이 깨질 수 있는 구조다
`Dockerfile`

```dockerfile
FROM openjdk:17-alpine
COPY . .
RUN ./gradlew build -x test
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar          # ← 빌드 결과가 아니라 "빌드 컨텍스트"에서 복사
```

- 마지막 `COPY`는 컨테이너 안에서 방금 만든 jar가 아니라 **호스트의 `build/`** 를 본다.
  `build/`는 `.gitignore` 대상이라 깨끗한 클론에서는 존재하지 않는다 → **빌드 실패**.
  호스트에 낡은 jar가 있으면 그게 배포된다 → 더 나쁘다.
- 싱글 스테이지라 최종 이미지에 소스·Gradle 캐시가 전부 들어간다.
- `openjdk` 공식 이미지는 지원 종료됐다 → `eclipse-temurin`.
- `.dockerignore`가 없어 `.git`, `.gradle`이 컨텍스트로 전송된다.
- 비루트 유저 없음, 헬스체크 없음.

### P2-13. docker-compose 구성이 프리티어 인스턴스에서 죽는다
`docker-compose.yml`

- `depends_on`에 `condition: service_healthy`가 없다 → 백엔드가 DB보다 먼저 떠서 초기 기동 실패.
- `image: mysql:latest` — 재현 불가능한 빌드.
- `version: "3.8"` — Compose V2에서 무의미(경고).
- `./mysql-init.d`가 `.gitignore` 대상이라 클론 시 빈 디렉터리가 생성되고 초기화가 조용히 누락된다.
- **t4g/t3.micro(1GiB RAM)에 JVM + MySQL 8을 함께 올리면 OOM으로 죽는다.**
  MySQL 8은 기본 버퍼 풀만 128MB, 실사용 400~600MB를 요구한다.
  → [07-architecture-decision.md](07-architecture-decision.md)에서 DB를 인스턴스 밖으로 뺀다.

### P2-14. 테스트가 사실상 없다
`src/test/java/com/ninemensmorris/NineMensMorrisApplicationTests.java`

`contextLoads()` 하나뿐이고, DB와 `JWT_SECRET_KEY` 등 환경변수가 없으면 이마저 실패한다.
그래서 `Dockerfile`이 `build -x test`로 건너뛴다 → **테스트가 한 번도 돌지 않는다.**

### P2-15. 리포지토리만 클론해서는 실행할 수 없다
`.gitignore:38-41`

`application.properties`, `application-local.yml`, `application-prod.yml`, `.env`가 전부 무시된다.
예시 파일도, 필요한 환경변수 목록도 없다. README에도 실행 방법이 없다.
필요한 변수는 코드에서 역추출해야 한다:
`JWT_SECRET_KEY`, `ACCESS_TOKEN_EXPIRATION`, `REFRESH_TOKEN_EXPIRATION`,
`ACCESS_TOKEN_HEADER`, `REFRESH_TOKEN_HEADER`, `DOMAIN`, `DEFAULT_PROFILE_IMAGE`,
`SPRING_DATASOURCE_*`, 카카오 OAuth client-id/secret.
→ `application-example.yml` 추가 필요.

### P2-16. 로깅이 빈약하고, 있는 것도 대부분 오용이다
애플리케이션 전체 로그 구문이 **6줄**이며 그중 4줄이 레벨을 잘못 쓰고 있다.
게임 도메인(`MorrisService`) 로그는 **0줄** — 승패도 점수 변동도 기록되지 않는다.
P0-1(점수 뒤집힘)이 오래 발견되지 않은 직접적 원인이다.
상세 진단과 로그 레벨 정책은 [10-logging-and-observability.md](10-logging-and-observability.md) 참고.

### P2-17. 헬스체크 엔드포인트가 없다
`spring-boot-starter-actuator` 미포함. `Dockerfile`에 `HEALTHCHECK`도, compose에 `healthcheck`도 없다.
nginx/ALB가 인스턴스 상태를 판단할 방법이 없고, P2-13의 기동 순서 문제도 해결할 수 없다.
`/actuator/health`만 열고 나머지는 차단하는 최소 구성이 필요하다.

### P2-18. DB 마이그레이션 도구가 없다
Flyway/Liquibase가 없다. `ddl-auto` 값도 확인 불가(설정 파일 부재)지만 정황상 `update`로 보인다.
운영 중 스키마 변경 이력이 남지 않고, 컬럼 삭제가 반영되지 않는다.
[08](08-guest-mode-design.md)에서 **`User`의 PK를 바꾸는 마이그레이션**이 필요하므로 반드시 도입해야 한다.
