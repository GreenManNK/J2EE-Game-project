package com.game.hub.games.quiz.service;

import com.game.hub.games.quiz.entity.HighScore;
import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.model.MultipleCorrectQuestion;
import com.game.hub.games.quiz.model.Question;
import com.game.hub.games.quiz.model.SingleCorrectQuestion;
import com.game.hub.games.quiz.model.TypedAnswerQuestion;
import com.game.hub.games.quiz.repository.HighScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuizService {
    private final List<Question> questions;
    private final Map<String, QuizRoom> rooms = new ConcurrentHashMap<>();

    @Autowired
    private HighScoreRepository highScoreRepository;

    public QuizService() {
        this.questions = loadQuestions();
    }

    public QuizRoom createRoom() {
        String roomId = UUID.randomUUID().toString();
        QuizRoom room = new QuizRoom(roomId, questions);
        rooms.put(roomId, room);
        return room;
    }

    public QuizRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public List<QuizRoom> getAvailableRooms() {
        return new ArrayList<>(rooms.values());
    }

    public void removeRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        rooms.remove(roomId);
    }

    public void saveHighScore(String playerName, int score) {
        highScoreRepository.save(new HighScore(playerName, score));
    }

    public List<HighScore> getHighScores() {
        return highScoreRepository.findTop10ByOrderByScoreDesc();
    }

    private List<Question> loadQuestions() {
        List<Question> questions = new ArrayList<>();
        questions.add(new SingleCorrectQuestion("What is the capital of France?", Arrays.asList("Berlin", "Madrid", "Paris", "Rome"), 2));
        questions.add(new SingleCorrectQuestion("What is 2 + 2?", Arrays.asList("3", "4", "5", "6"), 1));
        questions.add(new MultipleCorrectQuestion("Which of the following are primary colors?", Arrays.asList("Red", "Green", "Blue", "Yellow"), Arrays.asList(0, 2, 3)));
        questions.add(new TypedAnswerQuestion("What is the name of the largest planet in our solar system?", "Jupiter"));
        return questions;
    }
}
