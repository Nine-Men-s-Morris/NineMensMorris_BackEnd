package com.ninemensmorris.config;

import com.ninemensmorris.game.service.MorrisService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final MorrisService morrisService;

    @EventListener
    public void onConnectEvent(SessionConnectedEvent sessionConnectedEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(sessionConnectedEvent.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = accessor.getUser();

        String userId = principal.getName();
        morrisService.addSocket(sessionId, Long.parseLong(userId));
        System.out.println("소켓에 연결되었습니다. 유저 아이디: " + userId + ", 세션 아이디: " + sessionId);
    }

    @EventListener
    public void onDisconnectEvent(SessionDisconnectEvent sessionDisconnectEvent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(sessionDisconnectEvent.getMessage());
        String sessionId = accessor.getSessionId();
        Principal principal = accessor.getUser();

        String userId = principal.getName();
        morrisService.handleDisconnection(sessionId, Long.parseLong(userId));
        System.out.println("소켓 연결이 끊겼습니다. 유저 아이디: " + userId + ", 세션 아이디: " + sessionId);
    }
}
