/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormDirectoryService;
import dev.chojo.ember.feature.form.service.FormRespondents;
import dev.chojo.ember.feature.form.service.FormResponseExportService;
import dev.chojo.ember.feature.form.service.FormResultGrouping;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.read;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Answering a form, for oneself or for a member in one's care.
 *
 * <p>Saving an answer replaces the one on file, so the screen has to open the earlier answer to
 * correct it, and a first answer must never quietly overwrite one given already: that would lose
 * the answer, and on a form whose answers cannot be changed it would change it anyway.
 */
class ManagedMemberFormAnswerTest extends RepositoryTestBase {
    private static FormService formService;
    private static RouteHarness harness;
    private static Station station;
    private static Account guardianAccount;
    private static Account childAccount;
    private static Account strangerAccount;
    private static StationMember guardian;
    private static StationMember child;
    private static StationMember stranger;

    private int editableFormId;
    private int lockedFormId;
    private int editableQuestionId;
    private int lockedQuestionId;

    @BeforeAll
    static void setupClass() {
        formService = new FormService(
                formRepo,
                mock(StationMemberService.class),
                mock(MemberGroupService.class),
                mock(UserTagService.class),
                restrictionService,
                new DomainEventBus(Set.of()));
        var assembler = new FormAnalyticsAssembler(
                formService,
                new FormRespondents(stationMemberRepo, memberGroupRepo, userTagRepo, profileFieldRepo, stationRepo),
                new FormResultGrouping(memberGroupRepo, userTagRepo, profileFieldRepo),
                stationMemberRepo,
                accountRepo,
                memberIdentityFactory);

        station = stationRepo.create("ManagedAnswerStation");
        guardianAccount = accountRepo.create("managed-guardian@test.com", "Gerda", "Guardian");
        childAccount = accountRepo.create("managed-child@test.com", "Kim", "Child");
        strangerAccount = accountRepo.create("managed-stranger@test.com", "Sven", "Stranger");
        guardian = stationMemberRepo.create(station.id(), guardianAccount.id());
        child = stationMemberRepo.create(station.id(), childAccount.id());
        stranger = stationMemberRepo.create(station.id(), strangerAccount.id());

        stationMemberRepo.addManager(guardian.id(), child.id());
        harness = RouteHarness.serving(new FormRoutes(
                        formService,
                        new FormDirectoryService(formService),
                        new GuardianPolicy(stationMemberRepo),
                        assembler,
                        mock(FormResponseExportService.class),
                        mock(StationService.class),
                        mock(PageService.class)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(guardianAccount.id());
        accountRepo.delete(childAccount.id());
        accountRepo.delete(strangerAccount.id());
    }

    @BeforeEach
    void createForms() {
        editableFormId = publishedForm("Editable", true);
        lockedFormId = publishedForm("Locked", false);
        editableQuestionId = colourQuestion(editableFormId);
        lockedQuestionId = colourQuestion(lockedFormId);
    }

    @AfterEach
    void deleteForms() {
        formService.delete(editableFormId);
        formService.delete(lockedFormId);
    }

    private static int publishedForm(String title, boolean allowEdit) {
        var form = formService.create(
                station.id(), title, "desc", false, allowEdit, false, null, null, guardian.id(), FormPurpose.INTERNAL);
        formService.publish(form.id());
        return form.id();
    }

    private static int colourQuestion(int formId) {
        return formService
                .createQuestion(
                        formId,
                        0,
                        FormQuestionType.TEXT,
                        "Favourite colour?",
                        "",
                        false,
                        false,
                        new FormQuestionConfig.Text(false))
                .id();
    }

    /**
     * A caller holding what every member holds, so the route's own gate admits them and what is
     * tested is the handler's answer. A caller who is no member of the station is given it too.
     */
    private static UserSession sessionOf(StationMember member) {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                station.id(),
                null,
                member,
                Set.of(StationPermission.USER),
                Set.of(),
                null);
    }

    private static void seedAnswer(StationMember respondent, int formId, int questionId, String colour) {
        formService.submitResponse(
                formId, respondent.id(), guardian.id(), Map.of(questionId, new FormAnswerValue.TextAnswer(colour)));
    }

    private static String answerOf(StationMember respondent, int formId) {
        var response = formService.findResponse(formId, respondent.id()).orElseThrow();
        return formService.findAnswers(response.id()).getFirst().value();
    }

    private static String forChild(int formId) {
        return RouteHarness.PREFIX + "/forms/%d/respond/%d".formatted(formId, child.id());
    }

    private static JsonNode guardianReads(int formId) {
        return read(
                harness.request(client -> client.get(forChild(formId), harness.as(sessionOf(guardian)))),
                JsonNode.class);
    }

    private static String own(int formId) {
        return RouteHarness.PREFIX + "/forms/%d/respond".formatted(formId);
    }

    private static JsonNode answer(int questionId, String colour) {
        return body("""
                {"answers": {"%d": {"type": "TEXT", "text": "%s"}}}""".formatted(questionId, colour));
    }

    @Test
    void aGuardianReadsTheAnswerGivenForTheirChild() {
        seedAnswer(child, editableFormId, editableQuestionId, "Blue");

        var detail = guardianReads(editableFormId);

        assertEquals(child.id(), detail.path("response").path("memberId").asInt());
        assertEquals(1, detail.path("answers").size());
        assertTrue(detail.path("answers").get(0).path("value").asString().contains("Blue"));
    }

    @Test
    void aChildWhoHasNotAnsweredReadsAsEmpty() {
        var detail = guardianReads(editableFormId);

        assertFalse(detail.hasNonNull("response"));
        assertTrue(detail.path("answers").isEmpty());
    }

    @Test
    void aFirstAnswerForTheChildIsSaved() {
        var saved = harness.request(client -> client.post(
                forChild(editableFormId), answer(editableQuestionId, "Green"), harness.as(sessionOf(guardian))));

        assertEquals(201, saved.code());
        assertTrue(answerOf(child, editableFormId).contains("Green"));
    }

    @Test
    void aSecondFirstAnswerIsRefusedAndTheFirstStays() {
        seedAnswer(child, editableFormId, editableQuestionId, "Blue");

        var refused = harness.request(client -> client.post(
                forChild(editableFormId), answer(editableQuestionId, "Red"), harness.as(sessionOf(guardian))));

        assertEquals(Refusal.FORM_ANSWER_ALREADY_ON_FILE, refusalOf(refused));
        assertTrue(answerOf(child, editableFormId).contains("Blue"));
    }

    /** A form whose answers cannot be changed cannot be changed through a first answer either. */
    @Test
    void aLockedAnswerCannotBeReplacedByAnotherFirstAnswer() {
        seedAnswer(child, lockedFormId, lockedQuestionId, "Blue");

        var refused = harness.request(client ->
                client.post(forChild(lockedFormId), answer(lockedQuestionId, "Red"), harness.as(sessionOf(guardian))));

        assertEquals(Refusal.FORM_ANSWER_ALREADY_ON_FILE, refusalOf(refused));
        assertTrue(answerOf(child, lockedFormId).contains("Blue"));
    }

    @Test
    void anEditableAnswerIsCorrected() {
        seedAnswer(child, editableFormId, editableQuestionId, "Blue");

        var corrected = harness.request(client -> client.put(
                forChild(editableFormId), answer(editableQuestionId, "Red"), harness.as(sessionOf(guardian))));

        assertEquals(200, corrected.code());
        assertTrue(answerOf(child, editableFormId).contains("Red"));
    }

    @Test
    void aLockedAnswerIsNotChanged() {
        seedAnswer(child, lockedFormId, lockedQuestionId, "Blue");

        var refused = harness.request(client ->
                client.put(forChild(lockedFormId), answer(lockedQuestionId, "Red"), harness.as(sessionOf(guardian))));

        assertEquals(Refusal.FORM_ANSWER_NOT_CHANGEABLE_FOR_MEMBER, refusalOf(refused));
        assertTrue(answerOf(child, lockedFormId).contains("Blue"));
    }

    @Test
    void somebodyNotCaringForTheChildCannotReadTheirAnswer() {
        seedAnswer(child, editableFormId, editableQuestionId, "Blue");

        var refused = harness.request(client -> client.get(forChild(editableFormId), harness.as(sessionOf(stranger))));

        assertEquals(Refusal.MEMBER_NOT_YOURS_TO_ANSWER_FOR, refusalOf(refused));
    }

    @Test
    void somebodyNotCaringForTheChildCannotAnswerForThem() {
        var refused = harness.request(client -> client.post(
                forChild(editableFormId), answer(editableQuestionId, "Red"), harness.as(sessionOf(stranger))));

        assertEquals(Refusal.MEMBER_NOT_YOURS_TO_ANSWER_FOR, refusalOf(refused));
        assertTrue(formService.findResponse(editableFormId, child.id()).isEmpty());
    }

    @Test
    void somebodyOutsideTheStationIsRefused() {
        var refused = harness.request(client -> client.get(forChild(editableFormId), harness.as(sessionOf(null))));

        assertEquals(Refusal.NOT_A_MEMBER_READING_ANSWER_FOR_MEMBER, refusalOf(refused));
    }

    @Test
    void aMembersOwnFirstAnswerIsSaved() {
        var saved = harness.request(client ->
                client.post(own(editableFormId), answer(editableQuestionId, "Green"), harness.as(sessionOf(stranger))));

        assertEquals(201, saved.code());
        assertTrue(answerOf(stranger, editableFormId).contains("Green"));
    }

    /** The member's own answer is protected the same way, including on a form that locks it. */
    @Test
    void aMembersOwnSecondFirstAnswerIsRefusedAndTheFirstStays() {
        seedAnswer(stranger, lockedFormId, lockedQuestionId, "Blue");

        var refused = harness.request(client ->
                client.post(own(lockedFormId), answer(lockedQuestionId, "Red"), harness.as(sessionOf(stranger))));

        assertEquals(Refusal.FORM_ANSWER_ALREADY_ON_FILE, refusalOf(refused));
        assertTrue(answerOf(stranger, lockedFormId).contains("Blue"));
    }
}
