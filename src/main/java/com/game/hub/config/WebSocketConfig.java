package com.game.hub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final SessionPrincipalHandshakeHandler handshakeHandler;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(SessionPrincipalHandshakeHandler handshakeHandler,
                           @Value("${app.websocket.allowed-origin-patterns:*}") String allowedOriginPatterns) {
        this.handshakeHandler = handshakeHandler;
        this.allowedOriginPatterns = Arrays.stream(String.valueOf(allowedOriginPatterns).split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOriginPatterns.length == 0
            ? new String[]{"*"}
            : allowedOriginPatterns;
        registry.addEndpoint("/ws")
            .setHandshakeHandler(handshakeHandler)
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .setAllowedOriginPatterns(origins)
            .withSockJS()
            .setSessionCookieNeeded(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
