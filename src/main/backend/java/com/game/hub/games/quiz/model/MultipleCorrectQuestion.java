package com.game.hub.games.quiz.model;

import java.util.List;

public class MultipleCorrectQuestion extends Question {
    private List<Integer> correctAnswers;

    public MultipleCorrectQuestion(String question, List<String> options, List<Integer> correctAnswers) {
        super(question, options);
        this.correctAnswers = correctAnswers;
    }

    public List<Integer> getCorrectAnswers() {
        return correctAnswers;
    }

    public boolean isCorrect(List<Integer> answers) {
        return answers.containsAll(correctAnswers) && correctAnswers.containsAll(answers);
    }

    @Override
    public String getQuestionType() {
        return "multipleCorrect";
    }
}
