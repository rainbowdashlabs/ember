/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.QuestionAnswerCount;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Saving a form's questions changes what was changed and keeps the answers of every question that
 * is still there.
 */
class FormQuestionSaveTest extends RepositoryTestBase {
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
                new DomainEventBus(Set.of()),
                new ShareTokens());
        station = stationRepo.create("FormQuestionSaveStation");
        account = accountRepo.create("form-question-save@test.com", "Form", "Save");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void editingTheTitleKeepsTheAnswers() {
        var form = answeredForm();

        service.saveQuestions(
                form.id(), List.of(kept(form.first(), "First, spelled right"), kept(form.second(), "Second")));

        var questions = service.findQuestions(form.id());
        assertEquals(List.of(form.first().id(), form.second().id()), ids(questions));
        assertEquals("First, spelled right", questions.getFirst().title());
        assertEquals(Map.of(form.first().id(), 1, form.second().id(), 1), answersPerQuestion(form.id()));
    }

    @Test
    void reorderingKeepsTheAnswers() {
        var form = answeredForm();

        service.saveQuestions(form.id(), List.of(kept(form.second(), "Second"), kept(form.first(), "First")));

        var questions = service.findQuestions(form.id());
        assertEquals(List.of(form.second().id(), form.first().id()), ids(questions));
        assertEquals(
                List.of(0, 1), questions.stream().map(FormQuestion::position).toList());
        assertTrue(answerTo(form.id(), form.first().id()).contains("first answer"));
        assertTrue(answerTo(form.id(), form.second().id()).contains("second answer"));
    }

    @Test
    void removingOneQuestionDeletesOnlyItsAnswers() {
        var form = answeredForm();

        service.saveQuestions(form.id(), List.of(kept(form.first(), "First")));

        assertEquals(List.of(form.first().id()), ids(service.findQuestions(form.id())));
        assertEquals(Map.of(form.first().id(), 1), answersPerQuestion(form.id()));
        assertEquals(1, service.countResponses(form.id()));
    }

    @Test
    void aNewQuestionIsAddedBesideTheKeptOnes() {
        var form = answeredForm();

        service.saveQuestions(
                form.id(), List.of(kept(form.first(), "First"), kept(form.second(), "Second"), text(null, "Third")));

        var questions = service.findQuestions(form.id());
        assertEquals(3, questions.size());
        assertEquals("Third", questions.getLast().title());
        assertEquals(Map.of(form.first().id(), 1, form.second().id(), 1), answersPerQuestion(form.id()));
    }

    @Test
    void aQuestionOfAnotherFormIsRefusedAndNothingChanges() {
        var form = answeredForm();
        var other = answeredForm();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.saveQuestions(form.id(), List.of(kept(form.first(), "First"), kept(other.first(), "x"))));

        assertEquals(Refusal.QUESTION_NOT_ON_THIS_FORM, refused.refusal());
        assertEquals(List.of(form.first().id(), form.second().id()), ids(service.findQuestions(form.id())));
        assertEquals(List.of(other.first().id(), other.second().id()), ids(service.findQuestions(other.id())));
    }

    @Test
    void anExistingQuestionCannotChangeItsType() {
        var form = answeredForm();
        var retyped = new QuestionEntry(
                form.first().id(), FormQuestionType.DATE, "First", "", false, false, new FormQuestionConfig.Date());

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.saveQuestions(form.id(), List.of(retyped, kept(form.second(), "Second"))));

        assertEquals(Refusal.QUESTION_TYPE_NOT_CHANGEABLE, refused.refusal());
        assertEquals(
                FormQuestionType.TEXT,
                service.findQuestions(form.id()).getFirst().formQuestionType());
        assertEquals(Map.of(form.first().id(), 1, form.second().id(), 1), answersPerQuestion(form.id()));
    }

    @Test
    void countsTheAnswersOfEveryQuestion() {
        var form = answeredForm();
        service.saveQuestions(
                form.id(), List.of(kept(form.first(), "First"), kept(form.second(), "Second"), text(null, "Third")));
        var third = service.findQuestions(form.id()).getLast();

        assertEquals(
                List.of(
                        new QuestionAnswerCount(form.first().id(), 1),
                        new QuestionAnswerCount(form.second().id(), 1),
                        new QuestionAnswerCount(third.id(), 0)),
                service.countAnswersPerQuestion(form.id()));
    }

    /** A form with two text questions and one response answering both. */
    private static AnsweredForm answeredForm() {
        var form = service.create(
                station.id(), "Running poll", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL);
        service.saveQuestions(form.id(), List.of(text(null, "First"), text(null, "Second")));
        var questions = service.findQuestions(form.id());
        var first = questions.get(0);
        var second = questions.get(1);
        service.submitResponse(
                form.id(),
                member.id(),
                member.id(),
                Map.of(
                        first.id(), new FormAnswerValue.Text("first answer"),
                        second.id(), new FormAnswerValue.Text("second answer")));
        return new AnsweredForm(form.id(), first, second);
    }

    private static QuestionEntry kept(FormQuestion question, String title) {
        return text(question.id(), title);
    }

    private static QuestionEntry text(Integer id, String title) {
        return new QuestionEntry(
                id, FormQuestionType.TEXT, title, "", false, false, new FormQuestionConfig.Text(false));
    }

    private static List<Integer> ids(List<FormQuestion> questions) {
        return questions.stream().map(FormQuestion::id).toList();
    }

    private static Map<Integer, Integer> answersPerQuestion(int formId) {
        return service.findAllAnswersForForm(formId).stream()
                .collect(Collectors.toMap(FormAnswer::questionId, answer -> 1, Integer::sum));
    }

    private static String answerTo(int formId, int questionId) {
        return service.findAllAnswersForForm(formId).stream()
                .filter(answer -> answer.questionId() == questionId)
                .map(FormAnswer::value)
                .findFirst()
                .orElseThrow();
    }

    /** A form, and the two questions its one response answered. */
    private record AnsweredForm(int id, FormQuestion first, FormQuestion second) {}
}
