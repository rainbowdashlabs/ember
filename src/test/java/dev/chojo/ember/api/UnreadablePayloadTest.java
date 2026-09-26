/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The case somebody actually reported: answers that were sent and could not be read back, which
 * used to reach the reader as a bare failure with nothing in the body at all.
 *
 * <p>What is checked here is the half that has to be got right whatever the endpoint. The place the
 * reading stopped is named in the sender's own words, nothing of the type it failed to become comes
 * out with it, and whatever the library itself said stays behind.
 */
class UnreadablePayloadTest {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Answers to a form, in the shape a submission arrives in. */
    record Answers(Map<String, Answer> answers) {}

    /** One answer, whose rating has to be a number and has to be a rating. */
    record Answer(int rating) {
        Answer {
            if (rating < 1 || rating > 5) throw new IllegalArgumentException("A rating runs from one to five");
        }
    }

    /** A list of answers, to check that a position in one is named as a position. */
    record AnswerList(List<Answer> answers) {}

    @Test
    void aValueOfTheWrongKindNamesWhereTheReadingStopped() {
        var thrown = assertThrows(
                MismatchedInputException.class,
                () -> MAPPER.readValue("{\"answers\":{\"q7\":{\"rating\":\"five\"}}}", Answers.class));

        String where = Failures.fieldPath(thrown.getPath()).orElseThrow();
        assertTrue(where.startsWith("answers"), where + " does not start at the field that was sent");
        assertTrue(where.endsWith("rating"), where + " does not end at the value that could not be read");
    }

    @Test
    void aPositionInAListIsNamedAsAPosition() {
        var thrown = assertThrows(
                MismatchedInputException.class,
                () -> MAPPER.readValue("{\"answers\":[{\"rating\":1},{\"rating\":\"five\"}]}", AnswerList.class));

        assertTrue(
                Failures.fieldPath(thrown.getPath()).orElseThrow().contains("[1]"),
                "the second answer in the list is the one that could not be read");
    }

    @Test
    void aFieldNobodyAsksForIsNamedBackToTheSender() {
        var thrown = assertThrows(
                UnrecognizedPropertyException.class,
                () -> MAPPER.readValue("{\"answers\":{},\"mood\":\"cheerful\"}", Answers.class));

        assertEquals("mood", thrown.getPropertyName());
    }

    /**
     * A value the shape accepts and the thing itself refuses. The sentence written in the
     * constructor is the most useful thing there is to say, and it survives the gate that keeps
     * machinery out.
     */
    @Test
    void aRefusalWrittenInAConstructorReachesTheReader() {
        var thrown =
                assertThrows(ValueInstantiationException.class, () -> MAPPER.readValue("{\"rating\":9}", Answer.class));

        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertEquals(
                "A rating runs from one to five",
                Failures.readable(thrown.getCause().getMessage()).orElseThrow());
    }

    @Test
    void whatTheLibraryItselfSaysIsNeverWhatTheReaderIsShown() {
        var thrown = assertThrows(
                JacksonException.class,
                () -> MAPPER.readValue("{\"answers\":{\"q7\":{\"rating\":\"five\"}}}", Answers.class));

        assertTrue(
                Failures.readable(thrown.getMessage()).isEmpty(),
                "the exception's own words read as machinery and must not be shown");
        assertFalse(
                Failures.fieldPath(thrown.getPath()).orElseThrow().contains("Answer"),
                "the path must be the sender's words, not the type it failed to become");
    }

    @Test
    void aBodyThatIsNotJsonAtAllIsRefusedWithASentenceOfItsOwn() {
        assertThrows(JacksonException.class, () -> MAPPER.readValue("{not json", Answers.class));

        assertTrue(Failures.readable(Refusal.BODY_NOT_JSON.message()).isPresent());
        assertEquals(400, Refusal.BODY_NOT_JSON.status().getCode());
    }
}
