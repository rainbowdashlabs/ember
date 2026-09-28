/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.route.FormRoutes.SubmitRequest;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler.ResponseDetailDto;
import dev.chojo.ember.feature.form.service.FormRespondents;
import dev.chojo.ember.feature.form.service.FormResponseExportService;
import dev.chojo.ember.feature.form.service.FormResultGrouping;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.page.repository.PageRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.ShareTokens;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Answering a form, for oneself or for a member in one's care.
 *
 * <p>Saving an answer replaces the one on file, so the screen has to open the earlier answer to
 * correct it, and a first answer must never quietly overwrite one given already: that would lose
 * the answer, and on a form whose answers cannot be changed it would change it anyway.
 */
class ManagedMemberFormAnswerTest extends RepositoryTestBase {
    private static FormService formService;
    private static FormRoutes routes;
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
                new DomainEventBus(Set.of()),
                new ShareTokens());
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

        var routeMemberService = mock(StationMemberService.class);
        when(routeMemberService.findManaged(guardian.id())).thenReturn(List.of(child));
        routes = new FormRoutes(
                formService,
                routeMemberService,
                assembler,
                mock(FormResponseExportService.class),
                mock(StationRepository.class),
                mock(PageRepository.class));
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

    private static UserSession sessionOf(StationMember member) {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                station.id(),
                null,
                member,
                Set.of(),
                Set.of(),
                null);
    }

    private static void seedAnswer(StationMember respondent, int formId, int questionId, String colour) {
        formService.submitResponse(
                formId, respondent.id(), guardian.id(), Map.of(questionId, new FormAnswerValue.Text(colour)));
    }

    private static String answerOf(StationMember respondent, int formId) {
        var response = formService.findResponse(formId, respondent.id()).orElseThrow();
        return formService.findAnswers(response.id()).getFirst().value();
    }

    @SuppressWarnings("unchecked")
    private static Context request(StationMember caller, int formId, int questionId, String colour) {
        Context ctx = mock(Context.class);
        Validator<Integer> formParam = mock(Validator.class);
        when(formParam.get()).thenReturn(formId);
        Validator<Integer> memberParam = mock(Validator.class);
        when(memberParam.get()).thenReturn(child.id());
        when(ctx.pathParamAsClass("id", Integer.class)).thenReturn(formParam);
        when(ctx.pathParamAsClass("memberId", Integer.class)).thenReturn(memberParam);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(sessionOf(caller));
        when(ctx.status(any(HttpStatus.class))).thenReturn(ctx);
        when(ctx.bodyAsClass(SubmitRequest.class))
                .thenReturn(new SubmitRequest(Map.of(questionId, new FormAnswerValue.Text(colour))));
        return ctx;
    }

    private static void invoke(String handler, Context ctx) throws Exception {
        Method method = FormRoutes.class.getDeclaredMethod(handler, Context.class);
        method.setAccessible(true);
        try {
            method.invoke(routes, ctx);
        } catch (InvocationTargetException wrapped) {
            throw assertInstanceOf(Exception.class, wrapped.getCause());
        }
    }

    private static Refusal refusalOf(String handler, Context ctx) {
        return assertThrows(RefusalResponse.class, () -> invoke(handler, ctx)).refusal();
    }

    private static ResponseDetailDto detailOf(Context ctx) {
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(ctx).json(captor.capture());
        return assertInstanceOf(ResponseDetailDto.class, captor.getValue());
    }

    @Test
    void aGuardianReadsTheAnswerGivenForTheirChild() throws Exception {
        seedAnswer(child, editableFormId, editableQuestionId, "Blue");
        var ctx = request(guardian, editableFormId, editableQuestionId, "");

        invoke("getMemberResponse", ctx);

        var detail = detailOf(ctx);
        assertEquals(Integer.valueOf(child.id()), detail.response().memberId());
        assertEquals(1, detail.answers().size());
        assertTrue(detail.answers().getFirst().value().contains("Blue"));
    }

    @Test
    void aChildWhoHasNotAnsweredReadsAsEmpty() throws Exception {
        var ctx = request(guardian, editableFormId, editableQuestionId, "");

        invoke("getMemberResponse", ctx);

        var detail = detailOf(ctx);
        assertNull(detail.response());
        assertTrue(detail.answers().isEmpty());
    }

    @Test
    void aFirstAnswerForTheChildIsSaved() throws Exception {
        var ctx = request(guardian, editableFormId, editableQuestionId, "Green");

        invoke("submitForMember", ctx);

        verify(ctx).status(HttpStatus.CREATED);
        assertTrue(answerOf(child, editableFormId).contains("Green"));
    }

    @Test
    void aSecondFirstAnswerIsRefusedAndTheFirstStays() {
        seedAnswer(child, editableFormId, editableQuestionId, "Blue");

        var refusal = refusalOf("submitForMember", request(guardian, editableFormId, editableQuestionId, "Red"));

        assertEquals(Refusal.FORM_ANSWER_ALREADY_ON_FILE, refusal);
        assertTrue(answerOf(child, editableFormId).contains("Blue"));
    }

    /** A form whose answers cannot be changed cannot be changed through a first answer either. */
    @Test
    void aLockedAnswerCannotBeReplacedByAnotherFirstAnswer() {
        seedAnswer(child, lockedFormId, lockedQuestionId, "Blue");

        var refusal = refusalOf("submitForMember", request(guardian, lockedFormId, lockedQuestionId, "Red"));

        assertEquals(Refusal.FORM_ANSWER_ALREADY_ON_FILE, refusal);
        assertTrue(answerOf(child, lockedFormId).contains("Blue"));
    }

    @Test
    void anEditableAnswerIsCorrected() throws Exception {
        seedAnswer(child, editableFormId, editableQuestionId, "Blue");

        invoke("updateForMember", request(guardian, editableFormId, editableQuestionId, "Red"));

        assertTrue(answerOf(child, editableFormId).contains("Red"));
    }

    @Test
    void aLockedAnswerIsNotChanged() {
        seedAnswer(child, lockedFormId, lockedQuestionId, "Blue");

        var refusal = refusalOf("updateForMember", request(guardian, lockedFormId, lockedQuestionId, "Red"));

        assertEquals(Refusal.FORM_ANSWER_NOT_CHANGEABLE_FOR_MEMBER, refusal);
        assertTrue(answerOf(child, lockedFormId).contains("Blue"));
    }

    @Test
    void somebodyNotCaringForTheChildCannotReadTheirAnswer() {
        seedAnswer(child, editableFormId, editableQuestionId, "Blue");

        var refusal = refusalOf("getMemberResponse", request(stranger, editableFormId, editableQuestionId, ""));

        assertEquals(Refusal.MEMBER_NOT_YOURS_TO_ANSWER_FOR, refusal);
    }

    @Test
    void somebodyNotCaringForTheChildCannotAnswerForThem() {
        var refusal = refusalOf("submitForMember", request(stranger, editableFormId, editableQuestionId, "Red"));

        assertEquals(Refusal.MEMBER_NOT_YOURS_TO_ANSWER_FOR, refusal);
        assertTrue(formService.findResponse(editableFormId, child.id()).isEmpty());
    }

    @Test
    void somebodyOutsideTheStationIsRefused() {
        var refusal = refusalOf("getMemberResponse", request(null, editableFormId, editableQuestionId, ""));

        assertEquals(Refusal.NOT_A_MEMBER_READING_ANSWER_FOR_MEMBER, refusal);
    }

    @Test
    void aMembersOwnFirstAnswerIsSaved() throws Exception {
        var ctx = request(stranger, editableFormId, editableQuestionId, "Green");

        invoke("submitResponse", ctx);

        verify(ctx).status(HttpStatus.CREATED);
        assertTrue(answerOf(stranger, editableFormId).contains("Green"));
    }

    /** The member's own answer is protected the same way, including on a form that locks it. */
    @Test
    void aMembersOwnSecondFirstAnswerIsRefusedAndTheFirstStays() {
        seedAnswer(stranger, lockedFormId, lockedQuestionId, "Blue");

        var refusal = refusalOf("submitResponse", request(stranger, lockedFormId, lockedQuestionId, "Red"));

        assertEquals(Refusal.FORM_ANSWER_ALREADY_ON_FILE, refusal);
        assertTrue(answerOf(stranger, lockedFormId).contains("Blue"));
    }
}
