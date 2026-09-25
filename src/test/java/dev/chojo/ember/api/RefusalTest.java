/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefusalTest {

    @Test
    void everyRefusalSaysSomethingAReaderCanRead() {
        for (var refusal : Refusal.values()) {
            assertFalse(refusal.message().isBlank(), refusal + " says nothing");
            assertTrue(
                    Failures.readable(refusal.message()).isPresent(),
                    refusal + " says something that reads as machinery rather than as prose");
        }
    }

    /**
     * The status is how the reader is told whose problem this is, so a refusal that answers with a
     * success or a redirect would leave the question unanswered.
     */
    @Test
    void everyRefusalAnswersWithAFailingStatus() {
        for (var refusal : Refusal.values()) {
            assertTrue(refusal.status().getCode() >= 400, refusal + " does not answer with a failure");
        }
    }

    /**
     * A fault is the one thing a reader is asked to report, so anything that is really their own
     * doing must not be dressed as one.
     */
    @Test
    void onlyTheOnesThatReallyAreOursCallThemselvesFaults() {
        var faults = Arrays.stream(Refusal.values())
                .filter(refusal -> refusal.status() == HttpStatus.INTERNAL_SERVER_ERROR)
                .collect(Collectors.toSet());

        assertEquals(Set.of(Refusal.UNEXPECTED_FAULT, Refusal.UPLOAD_NOT_PROCESSED), faults);
    }

    @Test
    void raisingCarriesTheStatusTheSentenceAndTheName() {
        var raised = Refusal.FORM_NOT_HERE.raise();

        assertEquals(Refusal.FORM_NOT_HERE, raised.refusal());
        assertEquals(HttpStatus.NOT_FOUND.getCode(), raised.getStatus());
        assertEquals(Refusal.FORM_NOT_HERE.message(), raised.getMessage());
    }

    @Test
    void raisingWithADetailNamesItAfterTheSentence() {
        var raised = Refusal.BODY_UNEXPECTED_FIELD.raise("startsAt");

        assertEquals(Refusal.BODY_UNEXPECTED_FIELD.message() + ": startsAt", raised.getMessage());
        assertEquals(Refusal.BODY_UNEXPECTED_FIELD, raised.refusal());
    }

    @Test
    void theErrorBodyCarriesTheNameAsItsCode() {
        var body = ErrorResponseWrapper.of(Refusal.FORM_ANSWER_UNREADABLE);

        assertEquals("FORM_ANSWER_UNREADABLE", body.code());
        assertEquals(Refusal.FORM_ANSWER_UNREADABLE.message(), body.message());
        assertEquals(HttpStatus.BAD_REQUEST.getMessage(), body.error());
    }

    @Test
    void anUncodedBodyCarriesNoCodeAtAll() {
        var body = new ErrorResponseWrapper("Not Found", "Nothing here");

        assertNull(body.code());
        assertNull(body.reference());
    }

    /**
     * A refusal a screen has to tell apart from its neighbours is matched on its name, so the name
     * is the part that must not move. These are the ones something outside this repository reads.
     */
    @Test
    void theNamesOtherThingsMatchOnAreTheOnesTheyStillHave() {
        assertEquals("STATION_SLUG_TAKEN", Refusal.STATION_SLUG_TAKEN.name());
        assertEquals("PEER_DID_NOT_ANSWER", Refusal.PEER_DID_NOT_ANSWER.name());
        assertEquals("UNEXPECTED_FAULT", Refusal.UNEXPECTED_FAULT.name());
    }

    /**
     * Sitting a paper is the one place where being told something worked when it did not costs a
     * candidate the whole exam, so both refusals there say what became of the answers.
     */
    @Test
    void theExamRefusalsSayWhatBecameOfTheAnswers() {
        assertEquals(HttpStatus.CONFLICT, Refusal.QUIZ_ALREADY_HANDED_IN.status());
        assertEquals(HttpStatus.CONFLICT, Refusal.QUIZ_NOT_HANDED_IN.status());
        assertTrue(Refusal.QUIZ_ALREADY_HANDED_IN.message().contains("not saved"));
        assertTrue(Refusal.QUIZ_NOT_HANDED_IN.message().contains("could not be handed in"));
    }

    @Test
    void aRefusalThatNamesAWaitCarriesIt() {
        var body = ErrorResponseWrapper.of(
                Refusal.FORM_ANSWERED_TOO_OFTEN, Refusal.FORM_ANSWERED_TOO_OFTEN.message(), 30L);

        assertEquals(30L, body.retryAfterSeconds());
        assertEquals("FORM_ANSWERED_TOO_OFTEN", body.code());
    }
}
