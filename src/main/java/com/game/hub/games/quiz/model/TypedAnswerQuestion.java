package com.game.hub.games.quiz.model;

public class TypedAnswerQuestion extends Question {
    private String correctAnswer;

    public TypedAnswerQuestion(String question, String correctAnswer) {
        super(question, null);
        this.correctAnswer = correctAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public boolean isCorrect(String answer) {
        if (answer == null) {
            return false;
        }
        String expected = normalize(correctAnswer);
        String actual = normalize(answer);
        return expected.equalsIgnoreCase(actual);
    }

    @Override
    public String getQuestionType() {
        return "typedAnswer";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
