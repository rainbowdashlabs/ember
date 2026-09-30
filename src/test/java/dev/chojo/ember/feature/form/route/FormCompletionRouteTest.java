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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * A link after sending that is refused leaves nothing half written: no form is created, and a form
 * being changed keeps every setting it had.
 */
class FormCompletionRouteTest extends RepositoryTestBase {
    private static final String NOT_A_LINK = "javascript:alert(1)";

    private static FormService formService;
    private static RouteHarness harness;
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
                new DomainEventBus(Set.of()));
        station = stationRepo.create("FormCompletionRouteStation");
        account = accountRepo.create("completion-route@test.com", "Carla", "Completion");
        member = stationMemberRepo.create(station.id(), account.id());
        harness = RouteHarness.serving(new FormRoutes(
                        formService,
                        mock(GuardianPolicy.class),
                        mock(FormAnalyticsAssembler.class),
                        mock(FormResponseExportService.class),
                        mock(StationRepository.class),
                        mock(PageRepository.class)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aRefusedLinkCreatesNoForm() {
        int before = formService.findByStation(station.id()).size();

        var refused = harness.request(
                client -> client.post(RouteHarness.PREFIX + "/forms", form("Neu", NOT_A_LINK), harness.as(creator())));

        assertEquals(Refusal.FORM_COMPLETION_LINK_NOT_A_LINK, refusalOf(refused));
        assertEquals(before, formService.findByStation(station.id()).size());
    }

    @Test
    void aRefusedLinkChangesNothingElse() {
        int form = formService
                .create(station.id(), "Alt", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL)
                .id();

        var refused = harness.request(client ->
                client.put(RouteHarness.PREFIX + "/forms/" + form, form("Neu", NOT_A_LINK), harness.as(creator())));

        assertEquals(Refusal.FORM_COMPLETION_LINK_NOT_A_LINK, refusalOf(refused));
        assertEquals("Alt", formService.findById(form).orElseThrow().title());
    }

    private static FormRequest form(String title, String link) {
        return new FormRequest(title, "", false, true, false, null, null, FormPurpose.INTERNAL, null, link, null);
    }

    private static UserSession creator() {
        return new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                station.id(),
                null,
                member,
                Set.of(StationPermission.USER, StationPermission.POLL_CREATE),
                Set.of(),
                null);
    }
}
