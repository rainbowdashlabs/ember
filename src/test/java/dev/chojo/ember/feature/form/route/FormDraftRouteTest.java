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
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormDraft;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.route.FormRoutes.DraftRequest;
import dev.chojo.ember.feature.form.route.FormRoutes.DraftResponse;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormRespondents;
import dev.chojo.ember.feature.form.service.FormResponseExportService;
import dev.chojo.ember.feature.form.service.FormResultGrouping;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An unsent answer is private: only the member and whoever looks after them may read, keep or end it.
 * Managing the station's polls lets somebody answer for a member, never read what they have not sent.
 */
class FormDraftRouteTest extends RepositoryTestBase {
    private static FormService formService;
    private static FormRoutes routes;
    private static Station station;
    private static Account guardianAccount;
    private static Account childAccount;
    private static Account managerAccount;
    private static StationMember guardian;
    private static StationMember child;
    private static StationMember manager;

    private int formId;
    private int questionId;

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

        station = stationRepo.create("FormDraftRouteStation");
        guardianAccount = accountRepo.create("draft-route-guardian@test.com", "Gerda", "Guardian");
        childAccount = accountRepo.create("draft-route-child@test.com", "Kim", "Child");
        managerAccount = accountRepo.create("draft-route-manager@test.com", "Paula", "Polls");
        guardian = stationMemberRepo.create(station.id(), guardianAccount.id());
        child = stationMemberRepo.create(station.id(), childAccount.id());
        manager = stationMemberRepo.create(station.id(), managerAccount.id());

        stationMemberRepo.addManager(guardian.id(), child.id());
        routes = new FormRoutes(
                formService,
                new GuardianPolicy(stationMemberRepo),
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
        accountRepo.delete(managerAccount.id());
    }

    @BeforeEach
    void createForm() {
        formId = formService
                .create(station.id(), "Später", "", false, true, false, null, null, guardian.id(), FormPurpose.INTERNAL)
                .id();
        questionId = formService
                .createQuestion(
                        formId, 0, FormQuestionType.TEXT, "Frage", "", false, false, new FormQuestionConfig.Text(false))
                .id();
        formService.publish(formId);
    }

    @AfterEach
    void deleteForm() {
        formService.delete(formId);
    }

    @Test
    void aMemberKeepsAndReadsTheirOwnDraft() throws Exception {
        var save = request(child, Set.of(), "halb");
        invoke("saveDraft", save);
        verify(save).status(HttpStatus.NO_CONTENT);

        var read = request(child, Set.of(), "");
        invoke("getDraft", read);

        assertTrue(draftOf(read).answers().containsKey(questionId));
    }

    @Test
    void aGuardianKeepsAndReadsTheDraftOfTheirChild() throws Exception {
        invoke("saveDraftFor", request(guardian, Set.of(), "vom Vormund"));

        var read = request(guardian, Set.of(), "");
        invoke("getDraftFor", read);

        assertEquals(
                new FormAnswerValue.Text("vom Vormund"), draftOf(read).answers().get(questionId));
    }

    @Test
    void aPollManagerCannotReadTheDraftOfAMemberTheyDoNotLookAfter() throws Exception {
        invoke("saveDraft", request(child, Set.of(), "privat"));

        var refusal = refusalOf("getDraftFor", request(manager, Set.of(StationPermission.POLL_MANAGER), ""));

        assertEquals(Refusal.FORM_DRAFT_NOT_YOURS, refusal);
    }

    /** Running polls reads their results; it does not open a member's own answer to whoever runs them. */
    @Test
    void aPollManagerCannotReadTheAnswerOfAMemberTheyDoNotLookAfter() {
        var refusal = refusalOf("getMemberResponse", request(manager, Set.of(StationPermission.POLL_MANAGER), ""));

        assertEquals(Refusal.MEMBER_NOT_YOURS_TO_ANSWER_FOR, refusal);
    }

    @Test
    void aPollManagerCannotKeepOrEndTheDraftOfAMemberTheyDoNotLookAfter() throws Exception {
        invoke("saveDraft", request(child, Set.of(), "privat"));
        var managing = Set.of(StationPermission.POLL_MANAGER);

        assertEquals(Refusal.FORM_DRAFT_NOT_YOURS, refusalOf("saveDraftFor", request(manager, managing, "fremd")));
        assertEquals(Refusal.FORM_DRAFT_NOT_YOURS, refusalOf("discardDraftFor", request(manager, managing, "")));
        assertEquals(
                new FormAnswerValue.Text("privat"),
                formService
                        .findDraft(formId, child.id())
                        .orElseThrow()
                        .answers()
                        .get(questionId));
    }

    @Test
    void aClosedFormKeepsNoDraftAndHandsNoneOut() throws Exception {
        invoke("saveDraft", request(child, Set.of(), "halb"));
        formService.close(formId);

        assertEquals(Refusal.FORM_TAKES_NO_DRAFTS, refusalOf("saveDraft", request(child, Set.of(), "weiter")));
        var read = request(child, Set.of(), "");
        invoke("getDraft", read);
        assertNull(responseOf(read).draft());
    }

    private static UserSession sessionOf(StationMember member, Set<StationPermission> permissions) {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                station.id(),
                null,
                member,
                permissions,
                Set.of(),
                null);
    }

    @SuppressWarnings("unchecked")
    private Context request(StationMember caller, Set<StationPermission> permissions, String text) {
        Context ctx = mock(Context.class);
        Validator<Integer> formParam = mock(Validator.class);
        when(formParam.get()).thenReturn(formId);
        Validator<Integer> memberParam = mock(Validator.class);
        when(memberParam.get()).thenReturn(child.id());
        when(ctx.pathParamAsClass("id", Integer.class)).thenReturn(formParam);
        when(ctx.pathParamAsClass("memberId", Integer.class)).thenReturn(memberParam);
        when(ctx.attribute(ApiServer.ATTR_SESSION)).thenReturn(sessionOf(caller, permissions));
        when(ctx.status(any(HttpStatus.class))).thenReturn(ctx);
        when(ctx.bodyAsClass(DraftRequest.class))
                .thenReturn(new DraftRequest(Map.of(questionId, new FormAnswerValue.Text(text)), List.of("p0")));
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

    private static DraftResponse responseOf(Context ctx) {
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(ctx).json(captor.capture());
        return assertInstanceOf(DraftResponse.class, captor.getValue());
    }

    private static FormDraft draftOf(Context ctx) {
        var draft = responseOf(ctx).draft();
        assertNotNull(draft);
        return draft;
    }
}
