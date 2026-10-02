/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.RouteHarness;
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
import dev.chojo.ember.feature.form.route.PublicFormRoutes.PublicFormState;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.form.service.PublicFormRateLimiter;
import dev.chojo.ember.feature.form.service.PublicFormService;
import dev.chojo.ember.feature.form.service.SubmitterHashService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.read;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A public form that is not taking answers hands out its title and its state, and nothing of how it
 * asks: no pages, no questions with their branches, and not what it says once it is sent.
 */
class PublicFormWithholdingRouteTest extends RepositoryTestBase {
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
        station = stationRepo.create("PublicFormWithholdingStation");
        account = accountRepo.create("public-withholding@test.com", "Wilma", "Withheld");
        member = stationMemberRepo.create(station.id(), account.id());
        harness = RouteHarness.serving(new PublicFormRoutes(
                        formService,
                        new PublicFormService(formService, stationRepo),
                        mock(SubmitterHashService.class),
                        mock(PublicFormRateLimiter.class),
                        mock(ConsentService.class),
                        new Network(),
                        mock(StationLogoService.class)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void anOpenFormHandsOutItsPagesBranchesAndCompletion() {
        int form = branchingPoll();
        formService.publish(form);

        var view = viewOf(form);

        assertEquals(PublicFormState.OPEN.name(), view.path("state").asString());
        assertEquals(2, view.path("pages").size());
        assertTrue(view.path("questions").get(0).hasNonNull("branch"));
        assertEquals("Bis bald", view.path("completion").path("message").asString());
    }

    @Test
    void aFormNotPublishedYetWithholdsThem() {
        var view = viewOf(branchingPoll());

        assertEquals(PublicFormState.NOT_PUBLISHED.name(), view.path("state").asString());
        assertWithheld(view);
    }

    @Test
    void aClosedFormWithholdsThem() {
        int form = branchingPoll();
        formService.publish(form);
        formService.close(form);

        var view = viewOf(form);

        assertEquals(PublicFormState.CLOSED.name(), view.path("state").asString());
        assertWithheld(view);
    }

    private static void assertWithheld(JsonNode view) {
        assertTrue(view.path("pages").isEmpty(), "no pages");
        assertTrue(view.path("questions").isEmpty(), "no questions, so no branches");
        assertTrue(
                view.path("completion").isMissingNode()
                        || view.path("completion").isNull(),
                "no completion text");
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

    private static JsonNode viewOf(int form) {
        String token = formService.replaceShareLink(form, null).orElseThrow();
        return read(
                harness.request(client -> client.get(RouteHarness.PREFIX + "/public/shared-form/" + token)),
                JsonNode.class);
    }
}
