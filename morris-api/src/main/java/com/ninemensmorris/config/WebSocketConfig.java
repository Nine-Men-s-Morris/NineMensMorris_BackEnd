package com.ninemensmorris.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String CLIENT_PREFIX = "/app/";

    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 오리진을 "*" 로 열어두면 악성 사이트가 방문자의 인증 정보로 소켓을 열 수 있음 (CSWSH)
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ClientSendGuard());
    }

    // 클라이언트가 /topic 으로 직접 보내는 것을 막는다
    //
    // SimpleBroker 는 클라이언트가 /topic 으로 보낸 SEND 프레임도 구독자에게 그대로 중계한다
    // /app 접두사는 "여기로 보내면 @MessageMapping 이 받는다" 는 뜻일 뿐
    // 다른 곳으로 못 보낸다는 뜻이 아니다
    // 막지 않으면 브라우저 콘솔 한 줄로 상대 화면에 위조된 게임 상태를 띄울 수 있다
    private static final class ClientSendGuard implements ChannelInterceptor {

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            if (StompCommand.SEND.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith(CLIENT_PREFIX)) {
                    throw new IllegalArgumentException("클라이언트는 /app 으로만 전송할 수 있음: " + destination);
                }
            }
            return message;
        }
    }
}
