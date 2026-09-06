package com.ninemensmorris.config;

import com.ninemensmorris.game.service.MorrisService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final MorrisService morrisService;

    @EventListener
    public void onConnectEvent(SessionConnectedEvent sessionConnectedEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(sessionConnectedEvent.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = accessor.getUser();

        String userId = principal.getName();
        log.debug("소켓 연결 userId={} sessionId={}", userId, sessionId);
    }

    @EventListener
    public void onDisconnectEvent(SessionDisconnectEvent sessionDisconnectEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(sessionDisconnectEvent.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = accessor.getUser();

        String userId = principal.getName();
        morrisService.handleDisconnection(Long.parseLong(userId));
        log.debug("소켓 해제 userId={} sessionId={}", userId, sessionId);
    }
}
