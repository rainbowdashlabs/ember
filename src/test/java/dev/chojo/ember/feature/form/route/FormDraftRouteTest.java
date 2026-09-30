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
import dev.chojo.ember.feature.form.entity.FormDraft;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
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
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.EnumSet;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.read;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * An unsent answer is private: only the member and whoever looks after them may read, keep or end it.
 * Managing the station's polls lets somebody answer for a member, never read what they have not sent.
 */
class FormDraftRouteTest extends RepositoryTestBase {
    private static FormService formService;
    private static RouteHarness harness;
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
                new DomainEventBus(Set.of()));
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
        harness = RouteHarness.serving(new FormRoutes(
                        formService,
                        new GuardianPolicy(stationMemberRepo),
                        assembler,
                        mock(FormResponseExportService.class),
                        mock(StationRepository.class),
                        mock(PageRepository.class)))
                .withStations(stationRepo);
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
    void aMemberKeepsAndReadsTheirOwnDraft() {
        harness.run((server, client) -> {
            var saved = client.put(ownDraft(), draft("halb"), harness.as(sessionOf(child)));
            assertEquals(204, saved.code());

            var read = client.get(ownDraft(), harness.as(sessionOf(child)));

            assertTrue(draftOf(read).answers().containsKey(questionId));
        });
    }

    @Test
    void aGuardianKeepsAndReadsTheDraftOfTheirChild() {
        harness.run((server, client) -> {
            client.put(childsDraft(), draft("vom Vormund"), harness.as(sessionOf(guardian)));

            var read = client.get(childsDraft(), harness.as(sessionOf(guardian)));

            assertEquals(
                    new FormAnswerValue.Text("vom Vormund"),
                    draftOf(read).answers().get(questionId));
        });
    }

    @Test
    void aPollManagerCannotReadTheDraftOfAMemberTheyDoNotLookAfter() {
        harness.run((server, client) -> {
            client.put(ownDraft(), draft("privat"), harness.as(sessionOf(child)));

            var read = client.get(childsDraft(), harness.as(pollManager()));

            assertEquals(Refusal.FORM_DRAFT_NOT_YOURS, refusalOf(read));
        });
    }

    /** Running polls reads their results; it does not open a member's own answer to whoever runs them. */
    @Test
    void aPollManagerCannotReadTheAnswerOfAMemberTheyDoNotLookAfter() {
        harness.run((server, client) -> {
            var read = client.get(
                    RouteHarness.PREFIX + "/forms/%d/respond/%d".formatted(formId, child.id()),
                    harness.as(pollManager()));

            assertEquals(Refusal.MEMBER_NOT_YOURS_TO_ANSWER_FOR, refusalOf(read));
        });
    }

    @Test
    void aPollManagerCannotKeepOrEndTheDraftOfAMemberTheyDoNotLookAfter() {
        harness.run((server, client) -> {
            client.put(ownDraft(), draft("privat"), harness.as(sessionOf(child)));

            var kept = client.put(childsDraft(), draft("fremd"), harness.as(pollManager()));
            var ended = client.delete(childsDraft(), null, harness.as(pollManager()));

            assertEquals(Refusal.FORM_DRAFT_NOT_YOURS, refusalOf(kept));
            assertEquals(Refusal.FORM_DRAFT_NOT_YOURS, refusalOf(ended));
        });
        assertEquals(
                new FormAnswerValue.Text("privat"),
                formService
                        .findDraft(formId, child.id())
                        .orElseThrow()
                        .answers()
                        .get(questionId));
    }

    @Test
    void aClosedFormKeepsNoDraftAndHandsNoneOut() {
        harness.run((server, client) -> {
            client.put(ownDraft(), draft("halb"), harness.as(sessionOf(child)));
            formService.close(formId);

            var kept = client.put(ownDraft(), draft("weiter"), harness.as(sessionOf(child)));
            var read = client.get(ownDraft(), harness.as(sessionOf(child)));

            assertEquals(Refusal.FORM_TAKES_NO_DRAFTS, refusalOf(kept));
            assertNull(read(read, DraftResponse.class).draft());
        });
    }

    private String ownDraft() {
        return RouteHarness.PREFIX + "/forms/%d/draft".formatted(formId);
    }

    private String childsDraft() {
        return RouteHarness.PREFIX + "/forms/%d/draft/%d".formatted(formId, child.id());
    }

    private JsonNode draft(String text) {
        return body("""
                {"answers": {"%d": {"type": "TEXT", "text": "%s"}}, "path": ["p0"]}""".formatted(questionId, text));
    }

    private static UserSession pollManager() {
        return sessionOf(manager, StationPermission.POLL_MANAGER);
    }

    private static UserSession sessionOf(StationMember member, StationPermission... held) {
        var permissions = EnumSet.of(StationPermission.USER, held);
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

    private static FormDraft draftOf(Response response) {
        var draft = read(response, DraftResponse.class).draft();
        assertNotNull(draft);
        return draft;
    }
}
