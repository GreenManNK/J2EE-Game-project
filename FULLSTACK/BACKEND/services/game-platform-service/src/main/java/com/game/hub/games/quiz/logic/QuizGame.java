package com.game.hub.games.quiz.logic;

import com.game.hub.games.quiz.model.Question;
import com.game.hub.games.quiz.model.SingleCorrectQuestion;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class QuizGame {
    private List<Question> questions;
    private int currentQuestionIndex;
    private int score;
    private Timer timer;
    private final int TIME_LIMIT = 10; // 10 seconds per question

    public QuizGame(List<Question> questions) {
        this.questions = questions;
        this.currentQuestionIndex = 0;
        this.score = 0;
        this.timer = new Timer();
    }

    public void startGame() {
        if (questions != null && !questions.isEmpty()) {
            startQuestionTimer();
        }
    }

    public Question getCurrentQuestion() {
        if (questions != null && currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
    }

    public boolean answerQuestion(int answer) {
        timer.cancel();
        Question currentQuestion = getCurrentQuestion();
        if (currentQuestion instanceof SingleCorrectQuestion && ((SingleCorrectQuestion) currentQuestion).isCorrect(answer)) {
            score++;
            return true;
        }
        return false;
    }

    public void nextQuestion() {
        currentQuestionIndex++;
        if (currentQuestionIndex < questions.size()) {
            startQuestionTimer();
        } else {
            endGame();
        }
    }

    private void startQuestionTimer() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                nextQuestion();
            }
        }, TIME_LIMIT * 1000);
    }

    private void endGame() {
        // Game over logic
    }

    public int getScore() {
        return score;
    }
}
