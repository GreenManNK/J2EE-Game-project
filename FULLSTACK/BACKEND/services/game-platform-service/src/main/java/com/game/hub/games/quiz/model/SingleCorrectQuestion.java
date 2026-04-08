package com.game.hub.games.quiz.model;

import java.util.List;

public class SingleCorrectQuestion extends Question {
    private int correctAnswer;

    public SingleCorrectQuestion(String question, List<String> options, int correctAnswer) {
        super(question, options);
        this.correctAnswer = correctAnswer;
    }

    public int getCorrectAnswer() {
        return correctAnswer;
    }

    public boolean isCorrect(int answer) {
        return correctAnswer == answer;
    }

    @Override
    public String getQuestionType() {
        return "singleCorrect";
    }
}
