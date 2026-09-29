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
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.route.FormRoutes.FormRequest;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormResponseExportService;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A link after sending that is refused leaves nothing half written: no form is created, and a form
 * being changed keeps every setting it had.
 */
class FormCompletionRouteTest extends RepositoryTestBase {
    private static final String NOT_A_LINK = "javascript:alert(1)";

    private static FormService formService;
    private static FormRoutes routes;
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
        station = stationRepo.create("FormCompletionRouteStation");
        account = accountRepo.create("completion-route@test.com", "Carla", "Completion");
        member = stationMemberRepo.create(station.id(), account.id());
        routes = new FormRoutes(
                formService,
                mock(GuardianPolicy.class),
                mock(FormAnalyticsAssembler.class),
                mock(FormResponseExportService.class),
                mock(StationRepository.class),
                mock(PageRepository.class));
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aRefusedLinkCreatesNoForm() {
        int before = formService.findByStation(station.id()).size();

        var refusal = refusalOf("create", request(0, "Neu", NOT_A_LINK));

        assertEquals(Refusal.FORM_COMPLETION_LINK_NOT_A_LINK, refusal);
        assertEquals(before, formService.findByStation(station.id()).size());
    }

    @Test
    void aRefusedLinkChangesNothingElse() {
        int form = formService
                .create(station.id(), "Alt", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL)
                .id();

        var refusal = refusalOf("update", request(form, "Neu", NOT_A_LINK));

        assertEquals(Refusal.FORM_COMPLETION_LINK_NOT_A_LINK, refusal);
        assertEquals("Alt", formService.findById(form).orElseThrow().title());
    }

    @SuppressWarnings("unchecked")
    private static Context request(int formId, String title, String link) {
        Context ctx = mock(Context.class);
        Validator<Integer> formParam = mock(Validator.class);
        when(formParam.get()).thenReturn(formId);
        when(ctx.pathParamAsClass("id", Integer.class)).thenReturn(formParam);
        when(ctx.attribute(ApiServer.ATTR_SESSION))
                .thenReturn(new UserSession(
                        new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                        1,
                        station.id(),
                        null,
                        member,
                        Set.of(),
                        Set.of(),
                        null));
        when(ctx.status(any(HttpStatus.class))).thenReturn(ctx);
        when(ctx.bodyAsClass(FormRequest.class))
                .thenReturn(new FormRequest(
                        title, "", false, true, false, null, null, FormPurpose.INTERNAL, null, link, null));
        return ctx;
    }

    private static Refusal refusalOf(String handler, Context ctx) {
        return assertThrows(RefusalResponse.class, () -> {
                    Method method = FormRoutes.class.getDeclaredMethod(handler, Context.class);
                    method.setAccessible(true);
                    try {
                        method.invoke(routes, ctx);
                    } catch (InvocationTargetException wrapped) {
                        throw assertInstanceOf(Exception.class, wrapped.getCause());
                    }
                })
                .refusal();
    }
}
