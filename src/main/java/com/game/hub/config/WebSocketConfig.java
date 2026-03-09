package com.game.hub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final SessionPrincipalHandshakeHandler handshakeHandler;
    private final WebSocketAllowedOriginResolver allowedOriginResolver;

    public WebSocketConfig(SessionPrincipalHandshakeHandler handshakeHandler,
                           WebSocketAllowedOriginResolver allowedOriginResolver) {
        this.handshakeHandler = handshakeHandler;
        this.allowedOriginResolver = allowedOriginResolver;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOriginResolver.resolve();
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
