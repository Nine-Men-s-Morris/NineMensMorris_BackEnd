package com.ninemensmorris.config;

import com.ninemensmorris.game.service.GameService;
import com.ninemensmorris.security.AuthenticatedUser;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final GameService gameService;
    private final SimpMessagingTemplate messaging;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("소켓 연결 sessionId={}", accessor.getSessionId());
    }

    // 끊긴 사용자를 게임에서 정리한다
    // 기존에는 principal 이 null 이면 NPE 가 났고, 방만 지우고 승패는 남기지 않았다
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        AuthenticatedUser actor = AuthenticatedUser.from(principal);
        if (actor == null) {
            log.debug("비인증 소켓 해제 sessionId={}", accessor.getSessionId());
            return;
        }

        log.debug("소켓 해제 userId={} sessionId={}", actor.id(), accessor.getSessionId());
        gameService
                .handleDisconnect(actor.id())
                .ifPresent(
                        broadcast -> messaging.convertAndSend("/topic/rooms/" + broadcast.roomId(), broadcast.event()));
    }
}
