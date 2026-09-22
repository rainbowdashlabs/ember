/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler.ResultGroupDto;
import dev.chojo.ember.feature.form.service.FormResultQuery.Dimension;
import dev.chojo.ember.feature.form.service.FormResultQuery.Filter;
import dev.chojo.ember.feature.form.service.FormResultQuery.Grouping;
import dev.chojo.ember.feature.form.service.FormResultQuery.Match;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FormAnalyticsAssemblerTest extends RepositoryTestBase {

    private static FormService formService;
    private static FormAnalyticsAssembler assembler;
    private static Station station;
    private static Account submitter;
    private static Account guardian;
    private static StationMember submitterMember;
    private static StationMember guardianMember;
    private static int formId;
    private static int questionId;
    private static int responseId;

    @BeforeAll
    static void setupClass() {
        var eventBus = new DomainEventBus(Set.of());
        var memberService = mock(StationMemberService.class);
        var groupService = mock(MemberGroupService.class);
        var tagService = mock(UserTagService.class);

        formService = new FormService(formRepo, memberService, groupService, tagService, restrictionService, eventBus);
        assembler = new FormAnalyticsAssembler(
                formService,
                new FormRespondents(stationMemberRepo, memberGroupRepo, userTagRepo, profileFieldRepo, stationRepo),
                new FormResultGrouping(memberGroupRepo, userTagRepo, profileFieldRepo),
                stationMemberRepo,
                accountRepo,
                memberIdentityFactory);

        station = stationRepo.create("FormAssemblerStation");
        submitter = accountRepo.create("submitter@test.com", "Sam", "Submitter");
        guardian = accountRepo.create("guardian@test.com", "Gail", "Guardian");
        submitterMember = stationMemberRepo.create(station.id(), submitter.id());
        guardianMember = stationMemberRepo.create(station.id(), guardian.id());

        var form = formService.create(
                station.id(),
                "Analytics Form",
                "desc",
                false,
                true,
                false,
                null,
                null,
                guardianMember.id(),
                FormPurpose.INTERNAL);
        formId = form.id();
        formService.publish(formId);

        var question = formService.createQuestion(
                formId,
                0,
                FormQuestionType.TEXT,
                "Your favourite color?",
                "Pick one",
                true,
                false,
                new FormQuestionConfig.Text(false));
        questionId = question.id();

        var response = formService.submitResponse(
                formId,
                submitterMember.id(),
                guardianMember.id(),
                Map.of(questionId, new FormAnswerValue.Text("Blue")));
        responseId = response.id();
    }

    @AfterAll
    static void cleanupClass() {
        formService.delete(formId);
        stationRepo.delete(station.id());
        accountRepo.delete(submitter.id());
        accountRepo.delete(guardian.id());
    }

    @Test
    void buildAnalyticsAggregatesAnswers() {
        var analytics = assembler.buildAnalytics(formId);
        assertEquals(formId, analytics.formId());
        assertEquals(1, analytics.totalResponses());
        assertEquals(1, analytics.questions().size());
        var qa = analytics.questions().getFirst();
        assertEquals(questionId, qa.questionId());
        assertEquals(FormQuestionType.TEXT, qa.questionType());

        assertEquals(1, analytics.groups().size(), "an ungrouped view is one group of every response");
        var everyone = analytics.groups().getFirst();
        assertEquals(FormAnalyticsAssembler.ALL_RESPONSES, everyone.key());
        assertEquals(1, everyone.responseCount());
        assertEquals(List.of("Blue"), everyone.tallies().getFirst().values());
        assertFalse(analytics.groupsOverlap());
    }

    /**
     * The response was sent by a guardian for their child, so it is the child's group it counts in,
     * and a filter on a group the child is not in leaves nothing, missing members included.
     */
    @Test
    void resultsAreGroupedByTheMemberTheAnswerIsFor() {
        var youth = memberGroupRepo.create(station.id(), "Jugend");
        var parents = memberGroupRepo.create(station.id(), "Eltern");
        memberGroupRepo.addMember(youth.id(), submitterMember.id());
        memberGroupRepo.addMember(parents.id(), guardianMember.id());
        try {
            var grouped = assembler.buildAnalytics(
                    formId, new FormResultQuery(null, new Grouping(Dimension.GROUP, null, null, null)));
            assertEquals(
                    List.of(String.valueOf(youth.id())),
                    grouped.groups().stream().map(ResultGroupDto::key).toList());
            assertEquals("Jugend", grouped.groups().getFirst().label());
            assertEquals(
                    List.of("Blue"),
                    grouped.groups().getFirst().tallies().getFirst().values());
            assertTrue(grouped.groupsOverlap());

            var onlyParents = new Filter(null, List.of(parents.id()), Match.ANY, null, null, null, null, null);
            var filtered = assembler.buildAnalytics(formId, new FormResultQuery(onlyParents, null));
            assertEquals(0, filtered.totalResponses());
            assertTrue(filtered.responseIds().isEmpty());
        } finally {
            memberGroupRepo.delete(youth.id());
            memberGroupRepo.delete(parents.id());
        }
    }

    /** Age is counted from the station's date-of-birth question to the day of answering. */
    @Test
    void ageComesFromTheDateOfBirth() {
        var born = LocalDate.of(2010, 1, 1);
        int age = Period.between(born, LocalDate.now()).getYears();
        var field = profileFieldRepo.create(
                station.id(),
                "Geburtstag",
                ProfileFieldType.BIRTH_DATE,
                ProfileFieldConfig.parse("{}"),
                false,
                false,
                null);
        profileFieldRepo.setValue(submitterMember.id(), field.id(), StringNode.valueOf(born.toString()));
        try {
            var exactly = new Filter(null, null, null, null, null, null, age, age);
            assertEquals(
                    1,
                    assembler
                            .buildAnalytics(formId, new FormResultQuery(exactly, null))
                            .totalResponses());

            var grouped = assembler.buildAnalytics(
                    formId, new FormResultQuery(null, new Grouping(Dimension.AGE, null, null, List.of(age))));
            assertEquals(
                    List.of(age + "+"),
                    grouped.groups().stream().map(ResultGroupDto::key).toList());
        } finally {
            profileFieldRepo.delete(field.id());
        }
    }

    @Test
    void listResponsesPopulatesIdentityAndSubmittedBy() {
        var responses = assembler.listResponses(formId);
        assertEquals(1, responses.size());
        var entry = responses.getFirst();
        assertEquals(responseId, entry.id());
        assertEquals(submitterMember.id(), entry.memberId());
        assertEquals(guardianMember.id(), entry.submittedBy());
        assertNotNull(entry.submittedByName());
        assertNotNull(entry.memberIdentity());
    }

    @Test
    void getResponseDetailReturnsAnswers() {
        var detail = assembler.getResponseDetail(formId, responseId);
        assertEquals(responseId, detail.response().id());
        assertEquals(1, detail.answers().size());
    }

    @Test
    void getResponseDetailThrowsForUnknownResponse() {
        assertThrows(NotFoundResponse.class, () -> assembler.getResponseDetail(formId, 99999));
    }

    @Test
    void acknowledgeFillsIdentity() {
        formService.acknowledgeResponse(responseId, guardianMember.id());
        var detail = assembler.getResponseDetail(formId, responseId);
        assertNotNull(detail.response().acknowledgedAt());
        assertEquals(guardianMember.id(), detail.response().acknowledgedBy());
        assertNotNull(detail.response().acknowledgedByIdentity());
    }

    @Test
    void buildAnalyticsListsMissingResponsesForForcedForm() {
        var forced = formService.create(
                station.id(), "Forced", "", false, false, true, null, null, guardianMember.id(), FormPurpose.INTERNAL);
        formService.publish(forced.id());
        try {
            var analytics = assembler.buildAnalytics(forced.id());
            assertEquals(2, analytics.missingResponses().size());
        } finally {
            formService.delete(forced.id());
        }
    }

    @Test
    void listResponsesForAnonymousSubmissionHasNullMemberIdentity() {
        var poll = formService.create(
                station.id(), "Poll", "", false, false, false, null, null, guardianMember.id(), FormPurpose.POLL);
        formService.publish(poll.id());
        try {
            var question = formService.createQuestion(
                    poll.id(),
                    0,
                    FormQuestionType.TEXT,
                    "Pick one",
                    "",
                    true,
                    false,
                    new FormQuestionConfig.Text(false));
            var consent = new ConsentProof("v1", "v1", "v1", "127.0.0.1", "US", "test-agent", Instant.now());
            var response = formService.submitAnonymousResponse(
                    poll.id(), new byte[] {1, 2, 3, 4}, Map.of(question.id(), new FormAnswerValue.Text("A")), consent);

            var entry = assembler.listResponses(poll.id()).stream()
                    .filter(e -> e.id() == response.id())
                    .findFirst()
                    .orElseThrow();
            assertNull(entry.memberId());
            assertNull(entry.memberIdentity());
            assertNull(entry.submittedByName());
        } finally {
            formService.delete(poll.id());
        }
    }
}
