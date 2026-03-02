package com.game.hub.games.quiz.controller;

import com.game.hub.games.quiz.entity.HighScore;
import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/games/quiz")
public class QuizController {

    @Autowired
    private QuizService quizService;

    @GetMapping
    public String quizPage() {
        return "games/quiz";
    }

    @GetMapping("/highscores")
    @ResponseBody
    public List<HighScore> getHighScores() {
        return quizService.getHighScores();
    }

    @GetMapping("/rooms")
    @ResponseBody
    public List<Map<String, Object>> getAvailableRooms() {
        return quizService.getAvailableRooms().stream()
            .filter(room -> !room.getPlayers().isEmpty() || !room.getSpectators().isEmpty())
            .map(this::toRoomSummary)
            .toList();
    }

    private Map<String, Object> toRoomSummary(QuizRoom room) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", room.getRoomId());
        summary.put("playerCount", room.getPlayers().size());
        summary.put("spectatorCount", room.getSpectators().size());
        int totalQuestions = room.getTotalQuestions();
        int questionNumber = Math.min(room.getCurrentQuestionIndex() + 1, Math.max(totalQuestions, 1));
        summary.put("questionNumber", questionNumber);
        summary.put("totalQuestions", totalQuestions);
        summary.put("gameState", room.isGameOver() ? "FINISHED" : (room.getCurrentQuestionIndex() > 0 ? "PLAYING" : "WAITING"));
        return summary;
    }
}
