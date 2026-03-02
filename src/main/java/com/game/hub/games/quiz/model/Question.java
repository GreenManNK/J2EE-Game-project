package com.game.hub.games.quiz.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleCorrectQuestion.class, name = "multipleCorrect"),
    @JsonSubTypes.Type(value = TypedAnswerQuestion.class, name = "typedAnswer"),
    @JsonSubTypes.Type(value = SingleCorrectQuestion.class, name = "singleCorrect")
})
public abstract class Question {
    private String question;
    private List<String> options;

    public Question(String question, List<String> options) {
        this.question = question;
        this.options = options;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public abstract String getQuestionType();
}
