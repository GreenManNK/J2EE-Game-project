package com.game.hub.games.quiz.logic;

import com.game.hub.games.quiz.model.MultipleCorrectQuestion;
import com.game.hub.games.quiz.model.Question;
import com.game.hub.games.quiz.model.SingleCorrectQuestion;
import com.game.hub.games.quiz.model.TypedAnswerQuestion;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class QuizRoom {
    private static final int QUESTION_DURATION_SECONDS = 15;
    private static final long QUESTION_DURATION_MS = QUESTION_DURATION_SECONDS * 1000L;

    private final String roomId;
    private final List<Question> questions;
    private final Map<WebSocketSession, Integer> players;
    private final List<WebSocketSession> spectators;
    private final Set<String> answeredPlayerIds;
    private int currentQuestionIndex;
    private boolean started;
    private String hostPlayerId;
    private long questionStartedAtEpochMs;
    private long questionDeadlineEpochMs;
    private boolean winRewardGranted;

    public QuizRoom(String roomId, List<Question> questions) {
        this.roomId = roomId;
        this.questions = questions;
        this.players = new ConcurrentHashMap<>();
        this.spectators = new CopyOnWriteArrayList<>();
        this.answeredPlayerIds = ConcurrentHashMap.newKeySet();
        this.currentQuestionIndex = 0;
        this.started = false;
        this.hostPlayerId = null;
        this.questionStartedAtEpochMs = 0;
        this.questionDeadlineEpochMs = 0;
        this.winRewardGranted = false;
    }

    public boolean addPlayer(WebSocketSession session) {
        if (session == null) {
            return false;
        }
        String joiningPlayerId = playerKey(session);
        WebSocketSession existing = findPlayerSessionByKey(joiningPlayerId);
        Integer score = 0;
        if (existing != null) {
            Integer existingScore = players.remove(existing);
            answeredPlayerIds.remove(playerKey(existing));
            if (existingScore != null) {
                score = existingScore;
            }
        }
        players.put(session, score);
        spectators.removeIf(spectator -> isSameSession(spectator, session) || isSamePlayer(spectator, session));

        if (hostPlayerId == null || hostPlayerId.isBlank()) {
            hostPlayerId = joiningPlayerId;
        }
        return true;
    }
    
    public boolean addSpectator(WebSocketSession session) {
        if (session == null) {
            return false;
        }
        if (players.keySet().stream().anyMatch(player -> isSameSession(player, session) || isSamePlayer(player, session))) {
            return true;
        }
        if (spectators.stream().anyMatch(spectator -> isSameSession(spectator, session) || isSamePlayer(spectator, session))) {
            return true;
        }
        if (spectators.size() >= 4) {
            return false;
        }
        spectators.add(session);
        return true;
    }

    public void removePlayer(WebSocketSession session) {
        WebSocketSession playerSession = resolvePlayerSession(session);
        if (playerSession != null) {
            String removedPlayerId = playerKey(playerSession);
            players.remove(playerSession);
            answeredPlayerIds.remove(removedPlayerId);
            if (removedPlayerId.equals(hostPlayerId)) {
                hostPlayerId = players.keySet().stream()
                    .map(this::playerKey)
                    .findFirst()
                    .orElse(null);
            }
        }
        spectators.removeIf(spectator -> isSameSession(spectator, session) || isSamePlayer(spectator, session));
        answeredPlayerIds.remove(playerKey(session));
        if (players.isEmpty()) {
            started = false;
            currentQuestionIndex = 0;
            answeredPlayerIds.clear();
            questionStartedAtEpochMs = 0;
            questionDeadlineEpochMs = 0;
            winRewardGranted = false;
        }
    }

    public boolean startGame() {
        if (players.isEmpty()) {
            return false;
        }
        currentQuestionIndex = 0;
        started = true;
        answeredPlayerIds.clear();
        players.replaceAll((session, score) -> 0);
        winRewardGranted = false;
        openCurrentQuestionWindow();
        return true;
    }

    public boolean grantWinRewardOnce() {
        if (winRewardGranted || !isGameOver()) {
            return false;
        }
        winRewardGranted = true;
        return true;
    }

    public Question getCurrentQuestion() {
        synchronizeQuestionTimer();
        if (started && currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
    }

    public boolean answerQuestion(WebSocketSession session, Object answer, Integer questionNumber) {
        if (session == null) {
            return false;
        }
        synchronizeQuestionTimer();
        if (!started) {
            return false;
        }
        int activeQuestionNumber = currentQuestionIndex + 1;
        if (questionNumber != null && questionNumber != activeQuestionNumber) {
            return false;
        }
        String playerId = playerKey(session);
        if (answeredPlayerIds.contains(playerId)) {
            return false;
        }

        WebSocketSession playerSession = resolvePlayerSession(session);
        if (playerSession == null) {
            playerSession = findPlayerSessionByKey(playerId);
        }
        if (playerSession == null) {
            return false;
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
        return true;
    }

    public void nextQuestion() {
        if (!started) {
            return;
        }
        currentQuestionIndex++;
        answeredPlayerIds.clear();
        if (currentQuestionIndex >= questions.size()) {
            started = false;
            questionStartedAtEpochMs = 0;
            questionDeadlineEpochMs = 0;
            return;
        }
        openCurrentQuestionWindow();
    }

    public boolean hasEveryoneAnswered() {
        synchronizeQuestionTimer();
        if (!started || players.isEmpty()) {
            return false;
        }
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
        synchronizeQuestionTimer();
        return !started && currentQuestionIndex >= questions.size() && !questions.isEmpty();
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
        synchronizeQuestionTimer();
        return currentQuestionIndex;
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    public int getAnsweredCount() {
        return answeredPlayerIds.size();
    }

    public boolean isStarted() {
        synchronizeQuestionTimer();
        return started;
    }

    public String getHostPlayerId() {
        return hostPlayerId;
    }

    public long getQuestionStartedAtEpochMs() {
        synchronizeQuestionTimer();
        return questionStartedAtEpochMs;
    }

    public long getQuestionDeadlineEpochMs() {
        synchronizeQuestionTimer();
        return questionDeadlineEpochMs;
    }

    public int getQuestionDurationSeconds() {
        return QUESTION_DURATION_SECONDS;
    }

    public boolean synchronizeQuestionTimer() {
        if (!started) {
            return false;
        }
        if (questionDeadlineEpochMs <= 0) {
            openCurrentQuestionWindow();
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < questionDeadlineEpochMs) {
            return false;
        }
        nextQuestion();
        return true;
    }

    private void openCurrentQuestionWindow() {
        questionStartedAtEpochMs = System.currentTimeMillis();
        questionDeadlineEpochMs = questionStartedAtEpochMs + QUESTION_DURATION_MS;
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

    private WebSocketSession findPlayerSessionByKey(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return null;
        }
        for (WebSocketSession session : players.keySet()) {
            if (playerId.equals(playerKey(session))) {
                return session;
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

    private boolean isSamePlayer(WebSocketSession first, WebSocketSession second) {
        if (first == null || second == null) {
            return false;
        }
        return playerKey(first).equals(playerKey(second));
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
