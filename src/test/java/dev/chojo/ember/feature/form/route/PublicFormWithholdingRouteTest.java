/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.PageEntry;
import dev.chojo.ember.feature.form.entity.PageTarget;
import dev.chojo.ember.feature.form.entity.QuestionBranch;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.form.route.PublicFormRoutes.PublicForm;
import dev.chojo.ember.feature.form.route.PublicFormRoutes.PublicFormState;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.form.service.PublicFormRateLimiter;
import dev.chojo.ember.feature.form.service.SubmitterHashService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.ShareTokens;
import io.javalin.http.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A public form that is not taking answers hands out its title and its state, and nothing of how it
 * asks: no pages, no questions with their branches, and not what it says once it is sent.
 */
class PublicFormWithholdingRouteTest extends RepositoryTestBase {
    private static FormService formService;
    private static PublicFormRoutes routes;
    private static Station station;
    private static Account account;
    private static StationMember member;

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
        station = stationRepo.create("PublicFormWithholdingStation");
        account = accountRepo.create("public-withholding@test.com", "Wilma", "Withheld");
        member = stationMemberRepo.create(station.id(), account.id());
        routes = new PublicFormRoutes(
                formService,
                mock(StationRepository.class),
                mock(SubmitterHashService.class),
                mock(PublicFormRateLimiter.class),
                mock(ConsentService.class),
                mock(Network.class),
                mock(StationLogoService.class));
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void anOpenFormHandsOutItsPagesBranchesAndCompletion() throws Exception {
        int form = branchingPoll();
        formService.publish(form);

        var view = viewOf(form);

        assertEquals(PublicFormState.OPEN, view.state());
        assertEquals(2, view.pages().size());
        assertNotNull(view.questions().getFirst().branch());
        assertEquals("Bis bald", view.completion().message());
    }

    @Test
    void aFormNotPublishedYetWithholdsThem() throws Exception {
        var view = viewOf(branchingPoll());

        assertEquals(PublicFormState.NOT_PUBLISHED, view.state());
        assertWithheld(view);
    }

    @Test
    void aClosedFormWithholdsThem() throws Exception {
        int form = branchingPoll();
        formService.publish(form);
        formService.close(form);

        var view = viewOf(form);

        assertEquals(PublicFormState.CLOSED, view.state());
        assertWithheld(view);
    }

    private static void assertWithheld(PublicForm view) {
        assertTrue(view.pages().isEmpty(), "no pages");
        assertTrue(view.questions().isEmpty(), "no questions, so no branches");
        assertNull(view.completion(), "no completion text");
    }

    private static int branchingPoll() {
        int form = formService
                .create(station.id(), "Umfrage", "", false, true, false, null, null, member.id(), FormPurpose.POLL)
                .id();
        formService.setCompletion(form, "Bis bald", "/", null);
        formService.saveLayout(
                form,
                List.of(new PageEntry("p0", "", "", PageTarget.NEXT), new PageEntry("p1", "", "", PageTarget.NEXT)),
                List.of(new QuestionEntry(
                        null,
                        "p0",
                        FormQuestionType.CHOICE,
                        "Kommst du?",
                        "",
                        false,
                        false,
                        new FormQuestionConfig.Choice(
                                FormQuestionConfig.Option.numbered("Ja", "Nein"), false, false, false, null, null),
                        new QuestionBranch(Map.of("o1", PageTarget.SUBMIT)))));
        return form;
    }

    private static PublicForm viewOf(int form) throws Exception {
        String token = formService.replaceShareLink(form, null).orElseThrow();
        Context ctx = mock(Context.class);
        when(ctx.pathParam("token")).thenReturn(token);
        Method method = PublicFormRoutes.class.getDeclaredMethod("getSharedForm", Context.class);
        method.setAccessible(true);
        method.invoke(routes, ctx);
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(ctx).json(captor.capture());
        return assertInstanceOf(PublicForm.class, captor.getValue());
    }
}
