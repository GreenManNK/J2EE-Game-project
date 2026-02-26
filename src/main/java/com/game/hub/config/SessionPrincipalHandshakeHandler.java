package com.game.hub.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Component
public class SessionPrincipalHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object authUserId = attributes.get(RoleGuardInterceptor.AUTH_USER_ID);
        String userId = authUserId == null ? null : String.valueOf(authUserId).trim();
        if (userId != null && !userId.isEmpty()) {
            return () -> userId;
        }
        Object guestUserId = attributes.get("GUEST_USER_ID");
        String guestId = guestUserId == null ? null : String.valueOf(guestUserId).trim();
        if (guestId != null && !guestId.isEmpty()) {
            return () -> guestId;
        }
        return () -> "guest-" + UUID.randomUUID();
    }
}
