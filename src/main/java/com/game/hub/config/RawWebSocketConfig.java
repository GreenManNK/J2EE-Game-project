package com.game.hub.config;

import com.game.hub.games.cards.blackjack.websocket.BlackjackSocket;
import com.game.hub.games.quiz.websocket.QuizSocket;
import com.game.hub.games.typing.websocket.TypingSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private QuizSocket quizSocket;

    @Autowired
    private BlackjackSocket blackjackSocket;

    @Autowired
    private TypingSocket typingSocket;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(quizSocket, "/game/quiz").setAllowedOrigins("*");
        registry.addHandler(blackjackSocket, "/game/blackjack").setAllowedOrigins("*");
        registry.addHandler(typingSocket, "/game/typing").setAllowedOrigins("*");
    }
}
