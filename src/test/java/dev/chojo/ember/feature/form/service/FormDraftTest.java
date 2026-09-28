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
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.ShareTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A half-filled form is kept for later, never counted as an answer, and ends when the answer is sent,
 * when the reader starts over and when the form is closed.
 */
class FormDraftTest extends RepositoryTestBase {
    private static FormService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static StationMember guardian;

    @BeforeAll
    static void setup() {
        service = new FormService(
                formRepo,
                mock(StationMemberService.class),
                mock(MemberGroupService.class),
                mock(UserTagService.class),
                restrictionService,
                new DomainEventBus(Set.of()),
                new ShareTokens());
        station = stationRepo.create("FormDraftStation");
        account = accountRepo.create("form-draft@test.com", "Form", "Draft");
        member = stationMemberRepo.create(station.id(), account.id());
        guardian = stationMemberRepo.create(
                station.id(),
                accountRepo
                        .create("form-draft-guardian@test.com", "Form", "Guardian")
                        .id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void keepsTheAnswersOfItsOwnQuestionsAndThePagesItHas() {
        var form = textForm();
        var question = service.findQuestions(form).getFirst();

        service.saveDraft(
                form,
                member.id(),
                guardian.id(),
                Map.of(question.id(), new FormAnswerValue.Text("halb"), 999_999, new FormAnswerValue.Text("?")),
                List.of("p0", "nowhere"));

        var draft = service.findDraft(form, member.id()).orElseThrow();
        assertEquals(Map.of(question.id(), new FormAnswerValue.Text("halb")), draft.answers());
        assertEquals(List.of("p0"), draft.path());
        assertEquals(0, service.countResponses(form), "a draft is not an answer");
        assertFalse(service.hasResponded(form, member.id()));
    }

    @Test
    void aSecondSaveReplacesTheFirst() {
        var form = textForm();
        var question = service.findQuestions(form).getFirst();

        service.saveDraft(form, member.id(), member.id(), Map.of(question.id(), new FormAnswerValue.Text("a")), null);
        service.saveDraft(form, member.id(), member.id(), null, List.of("p0"));

        var draft = service.findDraft(form, member.id()).orElseThrow();
        assertEquals(Map.of(), draft.answers());
        assertEquals(List.of("p0"), draft.path());
    }

    @Test
    void endsWhenTheAnswerIsSent() {
        var form = textForm();
        var question = service.findQuestions(form).getFirst();
        service.saveDraft(
                form, member.id(), member.id(), Map.of(question.id(), new FormAnswerValue.Text("a")), List.of());

        service.submitResponse(form, member.id(), member.id(), Map.of(question.id(), new FormAnswerValue.Text("a")));

        assertTrue(service.findDraft(form, member.id()).isEmpty());
    }

    @Test
    void endsWhenTheReaderStartsOverOrTheFormIsClosed() {
        var form = textForm();
        service.saveDraft(form, member.id(), member.id(), Map.of(), List.of());
        service.saveDraft(form, guardian.id(), guardian.id(), Map.of(), List.of());

        service.discardDraft(form, member.id());
        assertTrue(service.findDraft(form, member.id()).isEmpty());
        assertTrue(service.findDraft(form, guardian.id()).isPresent());

        service.close(form);
        assertTrue(service.findDraft(form, guardian.id()).isEmpty());
    }

    @Test
    void endsOnceTheEndDateHasPassed() {
        var form = textForm();
        service.saveDraft(form, member.id(), member.id(), Map.of(), List.of("p0"));

        runOut(form);

        assertTrue(service.findDraft(form, member.id()).isEmpty(), "no draft of a form past its end");
        assertTrue(formRepo.findDraft(form, member.id()).isEmpty(), "asking ended it for good");
    }

    @Test
    void aFormOpenedAgainByItsDatesStartsEverybodyFresh() {
        var form = textForm();
        service.saveDraft(form, member.id(), member.id(), Map.of(), List.of("p0"));
        runOut(form);

        service.update(
                form, "Später", "", false, true, false, null, Instant.now().plus(1, ChronoUnit.DAYS));

        assertTrue(formRepo.findDraft(form, member.id()).isEmpty());
    }

    @Test
    void aFormThatKeepsTakingAnswersKeepsItsDrafts() {
        var form = textForm();
        service.saveDraft(form, member.id(), member.id(), Map.of(), List.of("p0"));

        service.update(form, "Umbenannt", "", false, true, false, null, null);

        assertTrue(service.findDraft(form, member.id()).isPresent());
    }

    /** Moves the end date into the past the way time does: without anybody saving the form. */
    private static void runOut(int form) {
        formRepo.update(
                form, "Später", "", false, true, false, null, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    private static int textForm() {
        int form = service.create(
                        station.id(), "Später", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL)
                .id();
        service.saveQuestions(
                form,
                List.of(new QuestionEntry(
                        null,
                        null,
                        FormQuestionType.TEXT,
                        "Frage",
                        "",
                        false,
                        false,
                        new FormQuestionConfig.Text(false),
                        null)));
        service.publish(form);
        return form;
    }
}
