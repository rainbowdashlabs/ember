/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefusalTest {
    private static final Pattern CODE = Pattern.compile("[A-Z]-\\d{3}");

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
     * The whole point of a code is that it names one line. Two constants sharing one would send
     * whoever reads a report back to guessing between them, which is the state this replaced.
     */
    @Test
    void noTwoRefusalsShareACode() {
        var seen = new HashMap<String, Refusal>();

        for (var refusal : Refusal.values()) {
            var clash = seen.put(refusal.code(), refusal);
            assertNull(clash, refusal + " and " + clash + " both answer with " + refusal.code());
        }
    }

    /**
     * A report outlives the code that produced it, so a number handed back out points every old
     * report at the wrong line. {@link RetiredRefusals} is the list of numbers that have been spent.
     */
    @Test
    void noRefusalTakesBackARetiredCode() {
        for (var refusal : Refusal.values()) {
            assertFalse(
                    RetiredRefusals.codes().contains(refusal.code()),
                    refusal + " has taken back " + refusal.code() + ", which a retired refusal used");
        }
    }

    /** Two areas claiming one letter would put two features' refusals under the same plate. */
    @Test
    void noTwoAreasClaimTheSameLetter() {
        var letters = new HashSet<Character>();

        for (var area : Refusal.Area.values()) {
            assertTrue(letters.add(area.letter()), area + " claims a letter another area already has");
        }
    }

    @Test
    void everyCodeIsALetterAHyphenAndThreeDigits() {
        for (var refusal : Refusal.values()) {
            assertTrue(CODE.matcher(refusal.code()).matches(), refusal + " is coded as " + refusal.code());
            assertEquals(refusal.area().letter(), refusal.code().charAt(0), refusal + " opens with the wrong letter");
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
     * doing must not be dressed as one. Two kinds of failure are ours: one nobody named, and an
     * upload that broke on the way in.
     */
    @Test
    void onlyTheOnesThatReallyAreOursCallThemselvesFaults() {
        var faults = Arrays.stream(Refusal.values())
                .filter(refusal -> refusal.status() == HttpStatus.INTERNAL_SERVER_ERROR)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        Refusal.UNEXPECTED_FAULT,
                        Refusal.UNEXPECTED_FAULT_FROM_UNKNOWN_STATE,
                        Refusal.AVATAR_NOT_PROCESSED,
                        Refusal.UPLOAD_NOT_PROCESSED),
                faults);
    }

    @Test
    void raisingCarriesTheStatusTheSentenceAndTheRefusal() {
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
    void theErrorBodyCarriesTheCode() {
        var body = ErrorResponseWrapper.of(Refusal.FORM_ANSWER_UNREADABLE);

        assertEquals(Refusal.FORM_ANSWER_UNREADABLE.code(), body.code());
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
        assertEquals(Refusal.FORM_ANSWERED_TOO_OFTEN.code(), body.code());
    }
}
