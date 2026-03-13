package com.game.hub.games.quiz.controller;

import com.game.hub.games.quiz.entity.HighScore;
import com.game.hub.games.quiz.logic.QuizRoom;
import com.game.hub.games.quiz.service.QuizService;
import com.game.hub.service.GameCatalogItem;
import com.game.hub.service.GameCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/games/quiz")
public class QuizController {
    private static final String GAME_CODE = "quiz";

    @Autowired
    private QuizService quizService;

    @Autowired
    private GameCatalogService gameCatalogService;

    @GetMapping
    public String quizPage(@RequestParam(required = false) String room,
                           @RequestParam(required = false) String mode,
                           Model model) {
        String normalizedRoomId = room == null ? "" : room.trim();
        if (!normalizedRoomId.isEmpty()) {
            StringBuilder redirect = new StringBuilder("redirect:/games/quiz/room/")
                .append(UriUtils.encodePathSegment(normalizedRoomId, StandardCharsets.UTF_8));
            if ("spectate".equalsIgnoreCase(mode)) {
                redirect.append("/spectate");
            }
            return redirect.toString();
        }
        return renderQuizPage(model);
    }

    @GetMapping("/room/{roomId}")
    public String quizRoomPage(@PathVariable String roomId, Model model) {
        return renderQuizPage(model);
    }

    @GetMapping("/room/{roomId}/spectate")
    public String quizSpectatePage(@PathVariable String roomId, Model model) {
        return renderQuizPage(model);
    }

    private String renderQuizPage(Model model) {
        populateCatalogModel(model);
        return "games/quiz";
    }

    private void populateCatalogModel(Model model) {
        GameCatalogItem game = gameCatalogService.findByCode(GAME_CODE).orElse(null);
        model.addAttribute("game", game);
        model.addAttribute("allGames", gameCatalogService.findAll());
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
        int questionNumber;
        if (room.isGameOver()) {
            questionNumber = totalQuestions;
        } else if (room.isStarted()) {
            questionNumber = Math.min(room.getCurrentQuestionIndex() + 1, Math.max(totalQuestions, 1));
        } else {
            questionNumber = 0;
        }
        summary.put("questionNumber", questionNumber);
        summary.put("totalQuestions", totalQuestions);
        summary.put("answeredCount", room.getAnsweredCount());
        summary.put("hostPlayerId", room.getHostPlayerId() == null ? "" : room.getHostPlayerId());
        summary.put("gameState", room.isGameOver() ? "FINISHED" : (room.isStarted() ? "PLAYING" : "WAITING"));
        return summary;
    }
}
