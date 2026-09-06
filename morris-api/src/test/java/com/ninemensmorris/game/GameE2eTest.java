package com.ninemensmorris.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.ninemensmorris.game.dto.response.MoveRejectedResponse;
import com.ninemensmorris.game.dto.response.RoomEvent;
import com.ninemensmorris.game.dto.response.RoomEventType;
import com.ninemensmorris.security.JwtProvider;
import com.ninemensmorris.support.IntegrationTestSupport;
import com.ninemensmorris.user.domain.User;
import com.ninemensmorris.user.repository.UserRepository;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// 실제 STOMP 연결로 한 판을 돌린다
// 서버 코드만 봐서는 확인되지 않는 것들(인증이 실제로 붙는지, 토픽이 실제로 전달되는지)을 잡는다
class GameE2eTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.ninemensmorris.match.repository.MatchRepository matchRepository;

    private WebSocketStompClient stompClient;
    private User host;
    private User guest;

    @BeforeEach
    void setUp() {
        // matches 가 users 를 참조하므로 먼저 지운다
        matchRepository.deleteAll();
        userRepository.deleteAll();
        host = userRepository.save(User.ofKakao(1001L, "host@test.com", "방장", null));
        guest = userRepository.save(User.ofKakao(1002L, "guest@test.com", "참가자", null));

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    // 핸드셰이크 요청에 JWT 쿠키를 실어 서블릿 필터가 Principal 을 채우게 한다
    private StompSession connect(User user) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add(HttpHeaders.COOKIE, "access_token=" + jwtProvider.generateAccessToken(user.getUserId()));

        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws", handshake, new StompSessionHandlerAdapter() {})
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private BlockingQueue<RoomEvent> subscribe(StompSession session, String destination) {
        BlockingQueue<RoomEvent> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return RoomEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((RoomEvent) payload);
            }
        });
        return received;
    }

    private RoomEvent next(BlockingQueue<RoomEvent> queue) throws InterruptedException {
        RoomEvent event = queue.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(event).as("이벤트가 도착하지 않음").isNotNull();
        return event;
    }

    @Test
    @DisplayName("두 사람이 붙어 게임을 시작하고 기권으로 끝낸다")
    void 한_판을_끝까지_진행한다() throws Exception {
        // given — 방을 만들고 둘 다 소켓에 붙는다
        long roomId = createRoomAndJoin();

        StompSession hostSession = connect(host);
        StompSession guestSession = connect(guest);
        BlockingQueue<RoomEvent> hostInbox = subscribe(hostSession, "/topic/rooms/" + roomId);
        BlockingQueue<RoomEvent> guestInbox = subscribe(guestSession, "/topic/rooms/" + roomId);
        Thread.sleep(300);

        // when — 방장이 시작한다
        hostSession.send("/app/rooms/" + roomId + "/start", null);

        // then — 두 사람 모두 시작 이벤트를 받는다
        RoomEvent started = next(hostInbox);
        assertThat(started.type()).isEqualTo(RoomEventType.STARTED);
        assertThat(next(guestInbox).type()).isEqualTo(RoomEventType.STARTED);

        long firstTurn = started.state().currentTurnId();
        StompSession firstSession = firstTurn == host.getUserId() ? hostSession : guestSession;

        // when — 선공이 돌을 놓는다
        firstSession.send("/app/rooms/" + roomId + "/place", new PlacePayload(0));

        // then — 판에 반영되고 턴이 넘어간다
        RoomEvent placed = next(hostInbox);
        assertThat(placed.type()).isEqualTo(RoomEventType.STATE_CHANGED);
        assertThat(placed.state().board()[0]).isIn("BLACK", "WHITE");
        assertThat(placed.state().currentTurnId()).isNotEqualTo(firstTurn);
        next(guestInbox);

        // when — 기권한다
        firstSession.send("/app/rooms/" + roomId + "/resign", null);

        // then — 종료 이벤트가 양쪽에 도착하고 승자가 상대다
        RoomEvent finished = next(hostInbox);
        assertThat(finished.type()).isEqualTo(RoomEventType.FINISHED);
        assertThat(finished.state().winnerId()).isNotEqualTo(firstTurn);
        assertThat(finished.state().endReason()).isEqualTo("RESIGN");
    }

    @Test
    @DisplayName("상대 차례에 둔 수는 거절되고 판이 바뀌지 않는다")
    void 남의_차례에_두면_거절된다() throws Exception {
        // given
        long roomId = createRoomAndJoin();

        StompSession hostSession = connect(host);
        StompSession guestSession = connect(guest);
        BlockingQueue<RoomEvent> hostInbox = subscribe(hostSession, "/topic/rooms/" + roomId);
        subscribe(guestSession, "/topic/rooms/" + roomId);
        Thread.sleep(300);

        hostSession.send("/app/rooms/" + roomId + "/start", null);
        RoomEvent started = next(hostInbox);

        long waitingTurn = started.state().currentTurnId() == host.getUserId() ? guest.getUserId() : host.getUserId();
        StompSession waitingSession = waitingTurn == host.getUserId() ? hostSession : guestSession;

        // 거절은 요청자에게만 가므로 개인 큐를 구독한다
        BlockingQueue<MoveRejectedResponse> errors = new LinkedBlockingQueue<>();
        waitingSession.subscribe("/user/queue/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MoveRejectedResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add((MoveRejectedResponse) payload);
            }
        });
        Thread.sleep(300);

        // when — 차례가 아닌 쪽이 둔다
        waitingSession.send("/app/rooms/" + roomId + "/place", new PlacePayload(0));

        // then — 거절 메시지가 오고 방에는 아무것도 브로드캐스트되지 않는다
        MoveRejectedResponse error = errors.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(error).as("거절 메시지가 오지 않음").isNotNull();
        assertThat(error.code()).isEqualTo("NOT_YOUR_TURN");
        assertThat(hostInbox.poll(1, TimeUnit.SECONDS)).as("거절인데 방에 브로드캐스트됨").isNull();
    }

    @Test
    @DisplayName("클라이언트는 /topic 으로 직접 발행할 수 없다")
    void 클라이언트는_토픽에_직접_발행할_수_없다() throws Exception {
        // given — 막지 않으면 브라우저 콘솔 한 줄로 상대 화면에 위조된 상태를 띄울 수 있다
        long roomId = createRoomAndJoin();

        StompSession attacker = connect(guest);
        StompSession victim = connect(host);
        BlockingQueue<RoomEvent> victimInbox = subscribe(victim, "/topic/rooms/" + roomId);
        Thread.sleep(300);

        // when — 공격자가 토픽으로 직접 위조 이벤트를 보낸다
        attacker.send("/topic/rooms/" + roomId, new ForgedEvent("FINISHED"));

        // then — 위조 이벤트는 도달하지 않는다
        // 가드가 예외를 던져 공격자 연결이 끊기므로 정상적인 PLAYER_LEFT 는 올 수 있다
        for (int attempt = 0; attempt < 3; attempt++) {
            RoomEvent received = victimInbox.poll(1, TimeUnit.SECONDS);
            if (received == null) {
                break;
            }
            assertThat(received.type()).as("클라이언트가 보낸 위조 이벤트가 그대로 중계됨").isNotEqualTo(RoomEventType.FINISHED);
        }
    }

    private long createRoomAndJoin() {
        var rest = new org.springframework.boot.test.web.client.TestRestTemplate();
        String base = "http://localhost:" + port + "/api/v1/rooms";

        var createHeaders = new HttpHeaders();
        createHeaders.add(HttpHeaders.COOKIE, "access_token=" + jwtProvider.generateAccessToken(host.getUserId()));
        var created = rest.exchange(
                base,
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(new CreateRoomPayload("E2E 방"), createHeaders),
                CreatedRoom.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long roomId = created.getBody().roomId();

        var joinHeaders = new HttpHeaders();
        joinHeaders.add(HttpHeaders.COOKIE, "access_token=" + jwtProvider.generateAccessToken(guest.getUserId()));
        var joined = rest.exchange(
                base + "/" + roomId + "/players",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(joinHeaders),
                Void.class);
        assertThat(joined.getStatusCode().value()).isEqualTo(201);

        return roomId;
    }

    private record CreateRoomPayload(String title) {}

    private record CreatedRoom(long roomId, String title) {}

    private record PlacePayload(int to) {}

    private record ForgedEvent(String type) {}
}
