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
import dev.chojo.ember.feature.form.entity.PageEntry;
import dev.chojo.ember.feature.form.entity.PageTarget;
import dev.chojo.ember.feature.form.entity.QuestionBranch;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * An answer decides which page comes next. The server walks the pages with the answers it receives,
 * stores the path taken, keeps only the answers on it and names every problem at its question.
 */
class FormBranchingTest extends RepositoryTestBase {
    private static final List<FormQuestionConfig.Option> COMING = FormQuestionConfig.Option.numbered("Ja", "Nein");

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
        station = stationRepo.create("FormBranchingStation");
        account = accountRepo.create("form-branching@test.com", "Form", "Branches");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void anAnswerLeadsToItsPageAndThePathIsStored() {
        var form = branchingForm();

        var response = service.submitResponse(
                form.id(), member.id(), member.id(), Map.of(form.coming().id(), choice("o1")));

        assertEquals(List.of("p0", "no"), response.path());
        assertEquals(
                List.of("p0", "no"),
                service.findResponse(form.id(), member.id()).orElseThrow().path());
    }

    /** A target sent without a kind is stored as the page below, which is also where the walk goes. */
    @Test
    void aTargetWithoutAKindLeadsOnToThePageBelow() {
        int form = newForm();
        service.saveLayout(
                form,
                List.of(page("p0"), page("p1")),
                List.of(question(null, "p0", single(), branch("o0", new PageTarget(null, null)))));
        int coming = service.findQuestions(form).getFirst().id();

        var response = service.submitResponse(form, member.id(), member.id(), Map.of(coming, choice("o0")));

        assertEquals(List.of("p0", "p1"), response.path());
        assertEquals(
                PageTarget.NEXT, service.findQuestions(form).getFirst().branch().targetOf("o0"));
    }

    @Test
    void takingTheOtherBranchOnEditDeletesTheAnswersOffThePath() {
        var form = branchingForm();
        service.submitResponse(
                form.id(),
                member.id(),
                member.id(),
                Map.of(form.coming().id(), choice("o0"), form.bringing().id(), new FormAnswerValue.Text("Kuchen")));

        service.submitResponse(
                form.id(),
                member.id(),
                member.id(),
                Map.of(
                        form.coming().id(), choice("o1"),
                        form.bringing().id(), new FormAnswerValue.Text("Kuchen"),
                        form.why().id(), new FormAnswerValue.Text("Urlaub")));

        var answers = service.findAllAnswersForForm(form.id()).stream()
                .map(FormAnswer::questionId)
                .toList();
        assertEquals(Set.of(form.coming().id(), form.why().id()), Set.copyOf(answers));
    }

    @Test
    void anAnswerSentEmptyOnEditIsDeleted() {
        var form = branchingForm();
        service.submitResponse(
                form.id(),
                member.id(),
                member.id(),
                Map.of(form.coming().id(), choice("o1"), form.why().id(), new FormAnswerValue.Text("Urlaub")));

        service.submitResponse(
                form.id(),
                member.id(),
                member.id(),
                Map.of(form.coming().id(), choice("o1"), form.why().id(), new FormAnswerValue.Text(" ")));

        assertEquals(1, service.findAllAnswersForForm(form.id()).size());
    }

    @Test
    void everyProblemIsNamedAtItsQuestion() {
        var form = branchingForm();

        var refused = assertThrows(
                FormAnswersRefused.class,
                () -> service.submitResponse(
                        form.id(),
                        member.id(),
                        member.id(),
                        Map.of(form.coming().id(), choice("o0"))));

        assertEquals(Refusal.FORM_ANSWER_REFUSED, refused.refusal());
        assertEquals(
                List.of(FormAnswersRefused.Problem.of(form.bringing().id(), "yes", Refusal.QUESTION_NEEDS_AN_ANSWER)),
                refused.problems());
        var renamed = refused.as(Refusal.FORM_ANSWERS_NOT_SAVED);
        assertEquals(Refusal.FORM_ANSWERS_NOT_SAVED, renamed.refusal());
        var body = assertInstanceOf(FormAnswersRefused.Body.class, renamed.body());
        assertEquals("F-035", body.code());
        assertEquals(refused.problems(), body.problems());
        assertEquals(List.of(), service.findAllAnswersForForm(form.id()), "nothing was stored");
    }

    @Test
    void anAnonymousAnswerKeepsItsPathToo() {
        var form = branchingForm();

        var response = service.submitAnonymousResponse(
                form.id(),
                new byte[] {1, 2, 3},
                Map.of(form.coming().id(), choice("o0"), form.bringing().id(), new FormAnswerValue.Text("Salat")),
                new ConsentProof("c", "p", "t", null, null, null, null));

        assertEquals(List.of("p0", "yes"), response.path());
        assertNull(response.memberId());
    }

    @Test
    void onlyASingleAnswerChoiceCanDecide() {
        int form = newForm();
        var pages = List.of(page("p0"), page("p1"));

        var multi = new FormQuestionConfig.Choice(COMING, true, false, false, null, null);
        assertRefused(
                Refusal.QUESTION_BRANCH_NOT_ON_A_SINGLE_CHOICE,
                form,
                pages,
                List.of(question(null, "p0", multi, branch("o0", PageTarget.SUBMIT))));
        assertRefused(
                Refusal.QUESTION_BRANCH_NOT_ON_A_SINGLE_CHOICE,
                form,
                pages,
                List.of(question(null, "p0", new FormQuestionConfig.Text(false), branch("o0", PageTarget.SUBMIT))));
        assertRefused(
                Refusal.QUESTION_BRANCH_NOT_ON_A_SINGLE_CHOICE,
                form,
                pages,
                List.of(question(null, "p0", single(), branch("elsewhere", PageTarget.SUBMIT))));
    }

    @Test
    void onePagesDecidesOnOneQuestionAndOnlyForward() {
        int form = newForm();
        var pages = List.of(page("p0"), page("p1"));

        assertRefused(
                Refusal.PAGE_BRANCHES_ON_TWO_QUESTIONS,
                form,
                pages,
                List.of(
                        question(null, "p0", single(), branch("o0", PageTarget.SUBMIT)),
                        question(null, "p0", single(), branch("o1", PageTarget.SUBMIT))));
        assertRefused(
                Refusal.FORM_PAGE_TARGET_NOT_FURTHER_DOWN,
                form,
                pages,
                List.of(question(null, "p1", single(), branch("o0", PageTarget.page("p0")))));
    }

    @Test
    void aStoredBranchIsReadBack() {
        var form = branchingForm();

        var branch = service.findQuestions(form.id()).getFirst().branch();

        assertEquals(PageTarget.page("yes"), branch.targetOf("o0"));
        assertNull(service.findQuestions(form.id()).getLast().branch());
    }

    private static void assertRefused(
            Refusal expected, int form, List<PageEntry> pages, List<QuestionEntry> questions) {
        var refused = assertThrows(RefusalResponse.class, () -> service.saveLayout(form, pages, questions));
        assertEquals(expected, refused.refusal());
    }

    /**
     * A form asking whether somebody is coming: yes leads to what they bring, no to why not, and both
     * send the form after that.
     */
    private static BranchingForm branchingForm() {
        int form = newForm();
        service.saveLayout(
                form,
                List.of(page("p0"), new PageEntry("yes", "", "", PageTarget.SUBMIT), page("no")),
                List.of(
                        question(
                                null,
                                "p0",
                                single(),
                                new QuestionBranch(Map.of("o0", PageTarget.page("yes"), "o1", PageTarget.page("no")))),
                        new QuestionEntry(
                                null,
                                "yes",
                                FormQuestionType.TEXT,
                                "Was bringst du mit?",
                                "",
                                true,
                                false,
                                new FormQuestionConfig.Text(false),
                                null),
                        new QuestionEntry(
                                null,
                                "no",
                                FormQuestionType.TEXT,
                                "Warum nicht?",
                                "",
                                false,
                                false,
                                new FormQuestionConfig.Text(false),
                                null)));
        var questions = service.findQuestions(form);
        return new BranchingForm(form, questions.get(0), questions.get(1), questions.get(2));
    }

    private static int newForm() {
        return service.create(
                        station.id(),
                        "Kommst du?",
                        "",
                        false,
                        true,
                        false,
                        null,
                        null,
                        member.id(),
                        FormPurpose.INTERNAL)
                .id();
    }

    private static FormQuestionConfig.Choice single() {
        return new FormQuestionConfig.Choice(COMING, false, false, false, null, null);
    }

    private static PageEntry page(String key) {
        return new PageEntry(key, "", "", PageTarget.NEXT);
    }

    private static QuestionBranch branch(String option, PageTarget target) {
        return new QuestionBranch(Map.of(option, target));
    }

    private static QuestionEntry question(
            Integer id, String pageKey, FormQuestionConfig config, QuestionBranch branch) {
        var type = config instanceof FormQuestionConfig.Choice ? FormQuestionType.CHOICE : FormQuestionType.TEXT;
        return new QuestionEntry(id, pageKey, type, "Kommst du?", "", false, false, config, branch);
    }

    private static FormAnswerValue choice(String key) {
        return new FormAnswerValue.Choice(List.of(key), "");
    }

    private record BranchingForm(int id, FormQuestion coming, FormQuestion bringing, FormQuestion why) {}
}
