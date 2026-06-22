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
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        var restrictionRepo = new RestrictionRepository(stationMemberRepo, memberGroupRepo, userTagRepo);

        formService = new FormService(formRepo, memberService, groupService, tagService, restrictionRepo, eventBus);
        assembler = new FormAnalyticsAssembler(formService, stationMemberRepo, accountRepo, memberIdentityFactory);

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
        assertEquals(1, qa.values().size());
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
}
