package com.caro.game.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final SessionPrincipalHandshakeHandler sessionPrincipalHandshakeHandler;

    @Value("${app.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://j2ee}")
    private String allowedOriginPatterns;

    public WebSocketConfig(SessionPrincipalHandshakeHandler sessionPrincipalHandshakeHandler) {
        this.sessionPrincipalHandshakeHandler = sessionPrincipalHandshakeHandler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOriginPatterns.split("\\s*,\\s*");
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(origins)
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .setHandshakeHandler(sessionPrincipalHandshakeHandler)
            .withSockJS();
    }
}
