package com.game.hub.games.quiz.logic;

import com.game.hub.games.quiz.model.MultipleCorrectQuestion;
import com.game.hub.games.quiz.model.Question;
import com.game.hub.games.quiz.model.SingleCorrectQuestion;
import com.game.hub.games.quiz.model.TypedAnswerQuestion;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QuizRoom {
    private final String roomId;
    private final List<Question> questions;
    private final Map<WebSocketSession, Integer> players;
    private final List<WebSocketSession> spectators;
    private final Set<String> answeredPlayerIds;
    private int currentQuestionIndex;

    public QuizRoom(String roomId, List<Question> questions) {
        this.roomId = roomId;
        this.questions = questions;
        this.players = new ConcurrentHashMap<>();
        this.spectators = new ArrayList<>();
        this.answeredPlayerIds = ConcurrentHashMap.newKeySet();
        this.currentQuestionIndex = 0;
    }

    public void addPlayer(WebSocketSession session) {
        players.putIfAbsent(session, 0);
    }
    
    public void addSpectator(WebSocketSession session) {
        if (spectators.size() < 4) {
            spectators.add(session);
        }
    }

    public void removePlayer(WebSocketSession session) {
        WebSocketSession playerSession = resolvePlayerSession(session);
        if (playerSession != null) {
            players.remove(playerSession);
            answeredPlayerIds.remove(playerKey(playerSession));
        }
        spectators.removeIf(spectator -> isSameSession(spectator, session));
        answeredPlayerIds.remove(playerKey(session));
    }

    public void startGame() {
        currentQuestionIndex = 0;
        answeredPlayerIds.clear();
        players.replaceAll((session, score) -> 0);
    }

    public Question getCurrentQuestion() {
        if (currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
    }

    public boolean answerQuestion(WebSocketSession session, Object answer) {
        if (session == null) {
            return false;
        }
        String playerId = playerKey(session);
        if (answeredPlayerIds.contains(playerId)) {
            return false;
        }

        WebSocketSession playerSession = resolvePlayerSession(session);
        if (playerSession == null) {
            if (players.size() == 1) {
                playerSession = players.keySet().iterator().next();
            } else {
                return false;
            }
        }

        Question currentQuestion = getCurrentQuestion();
        if (currentQuestion == null) {
            return false;
        }

        boolean correct = false;
        if (currentQuestion instanceof SingleCorrectQuestion singleCorrectQuestion) {
            if (answer instanceof Number number) {
                correct = singleCorrectQuestion.isCorrect(number.intValue());
            }
        } else if (currentQuestion instanceof MultipleCorrectQuestion multipleCorrectQuestion) {
            if (answer instanceof List<?> rawAnswers) {
                List<Integer> answers = rawAnswers.stream()
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .map(Number::intValue)
                        .toList();
                correct = multipleCorrectQuestion.isCorrect(answers);
            }
        } else if (currentQuestion instanceof TypedAnswerQuestion typedAnswerQuestion) {
            if (answer != null) {
                correct = typedAnswerQuestion.isCorrect(String.valueOf(answer));
            }
        }

        int currentScore = players.getOrDefault(playerSession, 0);
        int nextScore = correct ? (currentScore + 2) : Math.max(0, currentScore - 1);
        players.put(playerSession, nextScore);

        answeredPlayerIds.add(playerId);
        return correct;
    }

    public void nextQuestion() {
        currentQuestionIndex++;
        answeredPlayerIds.clear();
    }

    public boolean hasEveryoneAnswered() {
        if (players.isEmpty()) {
            return false;
        }
        long answeredCount = players.keySet().stream()
                .map(this::playerKey)
                .filter(answeredPlayerIds::contains)
                .count();
        return answeredCount == players.size();
    }

    public Map<WebSocketSession, Integer> getScores() {
        return players;
    }

    public boolean isGameOver() {
        return currentQuestionIndex >= questions.size();
    }

    public String getRoomId() {
        return roomId;
    }

    public Map<WebSocketSession, Integer> getPlayers() {
        return players;
    }
    
    public List<WebSocketSession> getSpectators() {
        return spectators;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    private WebSocketSession resolvePlayerSession(WebSocketSession session) {
        if (session == null) {
            return null;
        }
        if (players.containsKey(session)) {
            return session;
        }
        for (WebSocketSession candidate : players.keySet()) {
            if (isSameSession(candidate, session)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isSameSession(WebSocketSession first, WebSocketSession second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return String.valueOf(first.getId()).equals(String.valueOf(second.getId()));
    }

    private String playerKey(WebSocketSession session) {
        if (session == null) {
            return "";
        }
        if (session.getPrincipal() != null && session.getPrincipal().getName() != null) {
            String principalName = session.getPrincipal().getName().trim();
            if (!principalName.isEmpty()) {
                return principalName;
            }
        }
        return String.valueOf(session.getId());
    }
}
