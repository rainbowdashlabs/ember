/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPage;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormVisibility;
import dev.chojo.ember.feature.form.entity.PageEntry;
import dev.chojo.ember.feature.form.entity.PageTarget;
import dev.chojo.ember.feature.form.entity.QuestionBranch;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A form is copied as a draft with everything about how it asks, and nothing about who answered it.
 */
class FormDuplicateTest extends RepositoryTestBase {
    private static FormService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new FormService(
                formRepo,
                mock(StationMemberService.class),
                mock(MemberGroupService.class),
                mock(UserTagService.class),
                restrictionService,
                new DomainEventBus(Set.of()));
        station = stationRepo.create("FormDuplicateStation");
        account = accountRepo.create("form-duplicate@test.com", "Form", "Copies");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void copiesHowTheFormAsksAndLeavesItsAnswersBehind() {
        var source = service.create(
                station.id(),
                "Sommerfest",
                "Wer kommt?",
                true,
                false,
                true,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                member.id(),
                FormPurpose.INTERNAL);
        var coming = new FormQuestionConfig.Choice(
                FormQuestionConfig.Option.numbered("Ja", "Nein"), false, false, false, null, null);
        service.saveLayout(
                source.id(),
                List.of(
                        new PageEntry("start", "Start", "", PageTarget.NEXT),
                        new PageEntry("why", "Warum", "", PageTarget.SUBMIT)),
                List.of(
                        new QuestionEntry(
                                null,
                                "start",
                                FormQuestionType.CHOICE,
                                "Kommst du?",
                                "",
                                true,
                                true,
                                coming,
                                new QuestionBranch(Map.of("o0", PageTarget.SUBMIT))),
                        new QuestionEntry(
                                null,
                                "why",
                                FormQuestionType.TEXT,
                                "Warum nicht?",
                                "",
                                false,
                                false,
                                new FormQuestionConfig.Text(true),
                                null)));
        service.setRestrictions(
                source.id(),
                new RestrictionSelection(
                        List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND));
        service.updateRestrictionMode(source.id(), RestrictionMode.AND);
        service.publish(source.id());
        var first = service.findQuestions(source.id()).getFirst();
        service.submitResponse(
                source.id(),
                member.id(),
                member.id(),
                Map.of(first.id(), new FormAnswerValue.ChoiceAnswer(List.of("o0"), "")));

        var copy = service.duplicate(source.id(), "Kopie von Sommerfest", member.id())
                .orElseThrow();

        assertNotEquals(source.id(), copy.id());
        assertEquals("Kopie von Sommerfest", copy.title());
        assertEquals("Wer kommt?", copy.description());
        assertEquals(Form.FormStatus.DRAFT, copy.status());
        assertTrue(copy.shuffleQuestions());
        assertTrue(copy.forced());
        assertNull(copy.startAt());
        assertNull(copy.endAt());
        assertEquals(0, copy.responseCount());
        assertEquals(
                List.of("start", "why"),
                service.findPages(copy.id()).stream().map(FormPage::key).toList());
        var questions = service.findQuestions(copy.id());
        assertEquals(
                List.of("Kommst du?", "Warum nicht?"),
                questions.stream().map(FormQuestion::title).toList());
        assertEquals(PageTarget.SUBMIT, questions.getFirst().branch().targetOf("o0"));
        assertEquals(RestrictionMode.AND, copy.restrictionMode());
        assertEquals(
                List.of(StationUserType.MEMBER),
                service.findRestrictions(copy.id()).userTypes());
    }

    @Test
    void aPollKeepsHowFarItReaches() {
        var poll = service.create(
                station.id(), "Umfrage", "", false, true, false, null, null, member.id(), FormPurpose.POLL);
        service.setVisibility(poll.id(), FormVisibility.UNLISTED);

        var copy = service.duplicate(poll.id(), "Kopie", member.id()).orElseThrow();

        assertEquals(FormPurpose.POLL, copy.purpose());
        assertEquals(FormVisibility.UNLISTED, copy.visibility());
        assertTrue(service.shareLink(copy.id()).isEmpty(), "the link is not copied");
    }

    @Test
    void aFormThatIsNotThereIsNotCopied() {
        assertTrue(service.duplicate(-1, "Kopie", member.id()).isEmpty());
    }
}
