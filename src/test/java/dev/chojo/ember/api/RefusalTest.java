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
    private static final Pattern CODE = Pattern.compile("[A-Z]{1,2}-\\d{3}");
    private static final Pattern PREFIX = Pattern.compile("[A-Z]{1,2}");

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

    /**
     * A prefix is one letter or two, and nothing else.
     *
     * <p>The two widths are safe to mix because the hyphen ends the prefix, so nothing here has to
     * stop one prefix being the start of another. A third width would be a reader reading out an
     * eight-character code, which is where a code stops being worth quoting.
     */
    @Test
    void everyPrefixIsOneLetterOrTwo() {
        for (var area : Refusal.Area.values()) {
            assertTrue(PREFIX.matcher(area.prefix()).matches(), area + " is prefixed " + area.prefix());
        }
    }

    /** Two areas claiming one prefix would put two features' refusals under the same plate. */
    @Test
    void noTwoAreasClaimTheSamePrefix() {
        var prefixes = new HashSet<String>();

        for (var area : Refusal.Area.values()) {
            assertTrue(prefixes.add(area.prefix()), area + " claims a prefix another area already has");
        }
    }

    @Test
    void everyCodeIsAPrefixAHyphenAndThreeDigits() {
        for (var refusal : Refusal.values()) {
            assertTrue(CODE.matcher(refusal.code()).matches(), refusal + " is coded as " + refusal.code());
            assertTrue(
                    refusal.code().startsWith(refusal.area().prefix() + "-"), refusal + " opens with the wrong prefix");
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
     * doing must not be dressed as one. What is listed here is every failure that really is ours:
     * one nobody named, an upload that broke on the way in, a sheet that broke while it was being
     * drawn, and a row that was written and then could not be read back.
     *
     * <p>The list is long and written out on purpose. Adding a refusal that answers {@code 500} has
     * to be a decision somebody takes rather than a status they copy from the line above, because
     * the cost of getting it wrong is a report button on something the reader could have fixed
     * themselves, and a real fault buried among the reports it produces.
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
                        Refusal.UPLOAD_NOT_PROCESSED,
                        Refusal.EVENT_LIST_NOT_DRAWN,
                        Refusal.FORM_ANSWERS_NOT_EXPORTED,
                        Refusal.QUIZ_PICTURE_NOT_PROCESSED,
                        Refusal.QUIZ_TEST_PDF_NOT_MADE,
                        Refusal.QUIZ_TEST_SOLUTION_PDF_NOT_MADE,
                        Refusal.PROTOCOL_RUN_NOT_EXPORTED,
                        Refusal.CHECKLIST_PDF_NOT_MADE,
                        Refusal.CHECKLIST_PDF_INTERRUPTED,
                        Refusal.BLOG_FEED_NOT_MADE,
                        Refusal.TICKET_UPLOAD_NOT_READ,
                        Refusal.TICKET_ATTACHMENT_NOT_READ,
                        Refusal.LOST_ITEM_PICTURE_NOT_PROCESSED,
                        Refusal.FEED_NOT_BUILT,
                        Refusal.INSTANCE_STORAGE_MOVE_TAKEN_BACK,
                        Refusal.KB_PDF_STOPPED,
                        Refusal.KB_PDF_NOT_MADE,
                        Refusal.KB_PRESENTATION_NOT_REPLACED,
                        Refusal.KB_FOLDER_ICON_NOT_PROCESSED,
                        Refusal.KB_ARTICLE_IMAGE_NOT_PROCESSED,
                        Refusal.PUBLIC_KB_PDF_STOPPED,
                        Refusal.PUBLIC_KB_PDF_NOT_MADE,
                        Refusal.PARTNER_KB_PDF_STOPPED,
                        Refusal.PARTNER_KB_PDF_NOT_MADE,
                        Refusal.SESSION_STATION_NOT_HERE,
                        Refusal.FEDERATION_PARTNER_NOT_HERE_AFTER_SUSPENDING,
                        Refusal.FEDERATION_PARTNER_NOT_HERE_AFTER_RESUMING,
                        Refusal.LENDING_REQUEST_NOT_HERE_AFTER_APPROVAL,
                        Refusal.LENDING_REQUEST_NOT_HERE_AFTER_DECLINE,
                        Refusal.LENDING_REQUEST_NOT_HERE_AFTER_LENDING,
                        Refusal.LENDING_REQUEST_NOT_HERE_AFTER_RETURN,
                        Refusal.LENDING_REQUEST_NOT_HERE_AFTER_CLOSING,
                        Refusal.PAGE_NOT_HERE_AFTER_SAVE,
                        Refusal.PAGE_NOT_HERE_AFTER_VISIBILITY_CHANGE,
                        Refusal.PROCEDURE_NOT_HERE_AFTER_CHANGE,
                        Refusal.PROCEDURE_NOT_HERE_AFTER_RESOLVING,
                        Refusal.PROCEDURE_NOT_HERE_AFTER_REOPENING,
                        Refusal.MOVEMENT_NOT_HERE_AFTER_RECHAIN,
                        Refusal.EVENT_TEMPLATE_NOT_HERE_AFTER_CHANGE,
                        Refusal.FORM_NOT_HERE_AFTER_VISIBILITY_CHANGE,
                        Refusal.KB_FILE_NOT_HERE_AFTER_REUPLOAD,
                        Refusal.PROTOCOL_NOT_HERE_AFTER_CHANGE,
                        Refusal.PROTOCOL_RUN_NOT_HERE_AFTER_CHANGE,
                        Refusal.PROTOCOL_RUN_NOT_HERE_AFTER_CLOSING,
                        Refusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_LOCKING,
                        Refusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_UNLOCKING,
                        Refusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_COMPLETION),
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
