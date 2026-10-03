/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.refusal.AdminRefusal;
import dev.chojo.ember.api.refusal.BoardRefusal;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.api.refusal.ChecklistRefusal;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.FeedRefusal;
import dev.chojo.ember.api.refusal.FormRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.KnowledgeBaseRefusal;
import dev.chojo.ember.api.refusal.LostAndFoundRefusal;
import dev.chojo.ember.api.refusal.MailImportRefusal;
import dev.chojo.ember.api.refusal.MediaLibraryRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.NewsRefusal;
import dev.chojo.ember.api.refusal.PageRefusal;
import dev.chojo.ember.api.refusal.ProcedureRefusal;
import dev.chojo.ember.api.refusal.QuizRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RetiredRefusals;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.util.SizeParser;
import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.Test;

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
        for (var refusal : Refusal.all()) {
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

        for (var refusal : Refusal.all()) {
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
        for (var refusal : Refusal.all()) {
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
        for (var refusal : Refusal.all()) {
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
        for (var refusal : Refusal.all()) {
            assertTrue(refusal.status().getCode() >= 400, refusal + " does not answer with a failure");
        }
    }

    /**
     * A fault is the one thing a reader is asked to report, so anything that is really their own
     * doing must not be dressed as one. What is listed here is every failure that really is ours:
     * one nobody named, an upload that broke on the way in, a sheet that broke while it was being
     * drawn, a row that was written and then could not be read back, and a row the instance keeps
     * for itself, such as a permission, that is missing.
     *
     * <p>The list is long and written out on purpose. Adding a refusal that answers {@code 500} has
     * to be a decision somebody takes rather than a status they copy from the line above, because
     * the cost of getting it wrong is a report button on something the reader could have fixed
     * themselves, and a real fault buried among the reports it produces.
     */
    @Test
    void onlyTheOnesThatReallyAreOursCallThemselvesFaults() {
        var faults = Refusal.all().stream()
                .filter(refusal -> refusal.status() == HttpStatus.INTERNAL_SERVER_ERROR)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        GeneralRefusal.UNEXPECTED_FAULT,
                        GeneralRefusal.UNEXPECTED_FAULT_FROM_UNKNOWN_STATE,
                        MemberRefusal.AVATAR_NOT_PROCESSED,
                        MediaLibraryRefusal.UPLOAD_NOT_PROCESSED,
                        EventRefusal.EVENT_LIST_NOT_DRAWN,
                        FormRefusal.FORM_ANSWERS_NOT_EXPORTED,
                        QuizRefusal.QUIZ_PICTURE_NOT_PROCESSED,
                        QuizRefusal.QUIZ_TEST_PDF_NOT_MADE,
                        QuizRefusal.QUIZ_TEST_SOLUTION_PDF_NOT_MADE,
                        QuizRefusal.QUIZ_CATALOG_EXAMPLE_MISSING,
                        QuizRefusal.QUIZ_CATALOG_EXAMPLE_NOT_READ,
                        TestProtocolRefusal.PROTOCOL_RUN_NOT_EXPORTED,
                        ChecklistRefusal.CHECKLIST_PDF_NOT_MADE,
                        ChecklistRefusal.CHECKLIST_PDF_INTERRUPTED,
                        DocumentRefusal.DOCUMENT_RENDER_FAILED,
                        NewsRefusal.BLOG_FEED_NOT_MADE,
                        BoardRefusal.TICKET_UPLOAD_NOT_READ,
                        BoardRefusal.TICKET_ATTACHMENT_NOT_READ,
                        LostAndFoundRefusal.LOST_ITEM_PICTURE_NOT_PROCESSED,
                        FeedRefusal.FEED_NOT_BUILT,
                        StorageRefusal.INSTANCE_STORAGE_MOVE_TAKEN_BACK,
                        SystemRefusal.SETTINGS_NOT_SAVED,
                        KnowledgeBaseRefusal.KB_PDF_STOPPED,
                        KnowledgeBaseRefusal.KB_PDF_NOT_MADE,
                        KnowledgeBaseRefusal.KB_PRESENTATION_NOT_REPLACED,
                        KnowledgeBaseRefusal.KB_FOLDER_ICON_NOT_PROCESSED,
                        KnowledgeBaseRefusal.KB_ARTICLE_IMAGE_NOT_PROCESSED,
                        KnowledgeBaseRefusal.PUBLIC_KB_PDF_STOPPED,
                        KnowledgeBaseRefusal.PUBLIC_KB_PDF_NOT_MADE,
                        KnowledgeBaseRefusal.PARTNER_KB_PDF_STOPPED,
                        KnowledgeBaseRefusal.PARTNER_KB_PDF_NOT_MADE,
                        EventRefusal.SESSION_STATION_NOT_HERE,
                        FederationRefusal.FEDERATION_PARTNER_NOT_HERE_AFTER_SUSPENDING,
                        FederationRefusal.FEDERATION_PARTNER_NOT_HERE_AFTER_RESUMING,
                        FederationRefusal.LENDING_REQUEST_NOT_HERE_AFTER_APPROVAL,
                        FederationRefusal.LENDING_REQUEST_NOT_HERE_AFTER_DECLINE,
                        FederationRefusal.LENDING_REQUEST_NOT_HERE_AFTER_LENDING,
                        FederationRefusal.LENDING_REQUEST_NOT_HERE_AFTER_RETURN,
                        FederationRefusal.LENDING_REQUEST_NOT_HERE_AFTER_CLOSING,
                        PageRefusal.PAGE_NOT_HERE_AFTER_SAVE,
                        PageRefusal.PAGE_NOT_HERE_AFTER_VISIBILITY_CHANGE,
                        ProcedureRefusal.PROCEDURE_NOT_HERE_AFTER_CHANGE,
                        ProcedureRefusal.PROCEDURE_NOT_HERE_AFTER_RESOLVING,
                        ProcedureRefusal.PROCEDURE_NOT_HERE_AFTER_REOPENING,
                        InventoryRefusal.MOVEMENT_NOT_HERE_AFTER_RECHAIN,
                        EventRefusal.EVENT_TEMPLATE_NOT_HERE_AFTER_CHANGE,
                        FormRefusal.FORM_NOT_HERE_AFTER_VISIBILITY_CHANGE,
                        KnowledgeBaseRefusal.KB_FILE_NOT_HERE_AFTER_REUPLOAD,
                        TestProtocolRefusal.PROTOCOL_NOT_HERE_AFTER_CHANGE,
                        TestProtocolRefusal.PROTOCOL_RUN_NOT_HERE_AFTER_CHANGE,
                        TestProtocolRefusal.PROTOCOL_RUN_NOT_HERE_AFTER_CLOSING,
                        TestProtocolRefusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_LOCKING,
                        TestProtocolRefusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_UNLOCKING,
                        TestProtocolRefusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_COMPLETION,
                        QuizRefusal.AI_KEY_NOT_READ_BACK,
                        MailImportRefusal.MAILBOX_NOT_HERE_AFTER_SAVE,
                        MailImportRefusal.MAILBOX_RULE_NOT_HERE_AFTER_SAVE,
                        InventoryRefusal.INVENTORY_TAG_NOT_HERE_AFTER_CHANGE,
                        ClusterRefusal.CLUSTER_INVENTORY_TAG_NOT_HERE_AFTER_CHANGE,
                        EventRefusal.APPOINTMENT_DATE_ANSWER_NOT_HERE_AFTER_SAVE,
                        EventRefusal.APPOINTMENT_FIELD_NOT_HERE_AFTER_SELF_REGISTRATION,
                        KnowledgeBaseRefusal.KB_FAVOURITE_NOT_READ_BACK_AFTER_MARKING,
                        KnowledgeBaseRefusal.KB_PARTNER_FAVOURITE_NOT_READ_BACK_AFTER_MARKING,
                        ClusterRefusal.CLUSTER_MEMBER_GROUP_PERMISSION_UNKNOWN,
                        ClusterRefusal.CLUSTER_PERMISSION_NOT_KNOWN_ON_GRANT,
                        MemberRefusal.MANAGED_SIGN_IN_PERMISSION_MISSING,
                        StationRefusal.STATION_IMPORT_TARGET_NOT_HERE,
                        SystemRefusal.PROBLEM_PICTURE_NOT_KEPT),
                faults);
    }

    @Test
    void raisingCarriesTheStatusTheSentenceAndTheRefusal() {
        var raised = FormRefusal.FORM_NOT_HERE.raise();

        assertEquals(FormRefusal.FORM_NOT_HERE, raised.refusal());
        assertEquals(HttpStatus.NOT_FOUND.getCode(), raised.getStatus());
        assertEquals(FormRefusal.FORM_NOT_HERE.message(), raised.getMessage());
    }

    @Test
    void raisingWithADetailNamesItAfterTheSentence() {
        var raised = BodyRefusal.BODY_UNEXPECTED_FIELD.raise("startsAt");

        assertEquals(BodyRefusal.BODY_UNEXPECTED_FIELD.message() + ": startsAt", raised.getMessage());
        assertEquals(BodyRefusal.BODY_UNEXPECTED_FIELD, raised.refusal());
        assertEquals(RefusalDetail.text("startsAt"), raised.detail());
    }

    @Test
    void raisingWithoutADetailCarriesNone() {
        assertNull(FormRefusal.FORM_NOT_HERE.raise().detail());
        assertNull(FormRefusal.FORM_NOT_HERE.raise((String) null).detail());
    }

    @Test
    void aTypedDetailIsNamedInEnglishAndKeptAsAValue() {
        var room = RefusalDetail.room(0, 1024L * 1024 * 1024);
        var raised = ClusterRefusal.CLUSTER_QUOTA_GRANT_MORE_THAN_POOL.raise(room);

        assertEquals(
                ClusterRefusal.CLUSTER_QUOTA_GRANT_MORE_THAN_POOL.message() + ": 0 B free of "
                        + SizeParser.formatBytes(1024L * 1024 * 1024),
                raised.getMessage());
        var body = (ErrorResponseWrapper) raised.body();
        assertEquals(room, body.detail());
        assertEquals(raised.getMessage(), body.message());
    }

    @Test
    void aCountIsNamedWithItsUnit() {
        assertEquals("3", RefusalDetail.count(3).inEnglish());
        assertEquals(
                "31 days", RefusalDetail.count(31, RefusalDetail.CountUnit.DAYS).inEnglish());
        assertEquals(
                "line 2", RefusalDetail.count(2, RefusalDetail.CountUnit.LINE).inEnglish());
    }

    @Test
    void roomNeverGoesBelowNothing() {
        assertEquals(new RefusalDetail.RoomDetail(0, 10), RefusalDetail.room(-5, 10));
    }

    @Test
    void theErrorBodyCarriesTheCode() {
        var body = ErrorResponseWrapper.of(FormRefusal.FORM_ANSWER_UNREADABLE);

        assertEquals(FormRefusal.FORM_ANSWER_UNREADABLE.code(), body.code());
        assertEquals(FormRefusal.FORM_ANSWER_UNREADABLE.message(), body.message());
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
        assertEquals("STATION_SLUG_TAKEN", StationRefusal.STATION_SLUG_TAKEN.name());
        assertEquals("PEER_DID_NOT_ANSWER", AdminRefusal.PEER_DID_NOT_ANSWER.name());
        assertEquals("UNEXPECTED_FAULT", GeneralRefusal.UNEXPECTED_FAULT.name());
    }

    /**
     * Sitting a paper is the one place where being told something worked when it did not costs a
     * candidate the whole exam, so both refusals there say what became of the answers.
     */
    @Test
    void theExamRefusalsSayWhatBecameOfTheAnswers() {
        assertEquals(HttpStatus.CONFLICT, QuizRefusal.QUIZ_ALREADY_HANDED_IN.status());
        assertEquals(HttpStatus.CONFLICT, QuizRefusal.QUIZ_NOT_HANDED_IN.status());
        assertTrue(QuizRefusal.QUIZ_ALREADY_HANDED_IN.message().contains("not saved"));
        assertTrue(QuizRefusal.QUIZ_NOT_HANDED_IN.message().contains("could not be handed in"));
    }

    @Test
    void aRefusalThatNamesAWaitCarriesIt() {
        var body = ErrorResponseWrapper.of(
                FormRefusal.FORM_ANSWERED_TOO_OFTEN, FormRefusal.FORM_ANSWERED_TOO_OFTEN.message(), 30L);

        assertEquals(30L, body.retryAfterSeconds());
        assertEquals(FormRefusal.FORM_ANSWERED_TOO_OFTEN.code(), body.code());
    }
}
