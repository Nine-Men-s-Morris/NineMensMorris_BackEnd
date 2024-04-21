package com.ninemensmorris.config;

import com.ninemensmorris.game.service.MorrisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

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
        morrisService.addSocket(Long.parseLong(userId), sessionId);
        log.info("소켓에 연결되었습니다. 유저 아이디: {}, 세션 아이디: {}", userId, sessionId);
    }

    @EventListener
    public void onDisconnectEvent(SessionDisconnectEvent sessionDisconnectEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(sessionDisconnectEvent.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = accessor.getUser();

        String userId = principal.getName();
        morrisService.handleDisconnection(Long.parseLong(userId), sessionId);
        log.info("소켓 연결이 끊겼습니다. 유저 아이디: {}, 세션 아이디: {}", userId, sessionId);
    }
}
