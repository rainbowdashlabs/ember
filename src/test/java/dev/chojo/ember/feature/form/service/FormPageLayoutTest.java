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
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPage;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.PageEntry;
import dev.chojo.ember.feature.form.entity.PageTarget;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * A form is made of pages. Saving keeps pages by their key the way it keeps questions by their id,
 * puts every question on the page it names, and refuses pages that lead anywhere but further down.
 */
class FormPageLayoutTest extends RepositoryTestBase {
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
        station = stationRepo.create("FormPageLayoutStation");
        account = accountRepo.create("form-page-layout@test.com", "Form", "Pages");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aNewFormHasOnePage() {
        int form = newForm();

        var pages = service.findPages(form);

        assertEquals(1, pages.size());
        assertEquals("p0", pages.getFirst().key());
        assertEquals(PageTarget.NEXT, pages.getFirst().after());
    }

    @Test
    void questionsStandOnThePagesTheyName() {
        int form = newForm();

        service.saveLayout(
                form,
                List.of(page("p0", PageTarget.page("third")), page("second", PageTarget.NEXT), page("third", null)),
                List.of(text(null, "third", "C"), text(null, "p0", "A"), text(null, "second", "B")));

        var pages = service.findPages(form);
        assertEquals(List.of("p0", "second", "third"), keys(pages));
        assertEquals(PageTarget.page("third"), pages.getFirst().after());
        assertEquals(PageTarget.NEXT, pages.getLast().after(), "a page sent without a target leads on");
        var questions = service.findQuestions(form);
        assertEquals(List.of("A", "B", "C"), titles(questions), "questions come page by page");
        assertEquals(
                List.of("p0", "second", "third"),
                questions.stream().map(FormQuestion::pageKey).toList());
    }

    @Test
    void aQuestionMovedToAnotherPageKeepsItsAnswer() {
        int form = newForm();
        service.saveLayout(form, List.of(page("p0", null), page("later", null)), List.of(text(null, "p0", "A")));
        var question = service.findQuestions(form).getFirst();
        service.submitResponse(form, member.id(), member.id(), Map.of(question.id(), new FormAnswerValue.Text("x")));

        service.saveLayout(
                form, List.of(page("p0", null), page("later", null)), List.of(text(question.id(), "later", "A")));

        var moved = service.findQuestions(form).getFirst();
        assertEquals(question.id(), moved.id());
        assertEquals("later", moved.pageKey());
        assertEquals(1, service.findAllAnswersForForm(form).size());
    }

    @Test
    void aPageLeftOutIsRemovedAndAPageKeptIsChangedInPlace() {
        int form = newForm();
        service.saveLayout(form, List.of(page("p0", null), page("gone", null)), List.of(text(null, "gone", "A")));
        int kept = service.findPages(form).getFirst().id();

        service.saveLayout(form, List.of(new PageEntry("p0", "Start", "Hallo", PageTarget.SUBMIT)), List.of());

        var pages = service.findPages(form);
        assertEquals(List.of("p0"), keys(pages));
        assertEquals(kept, pages.getFirst().id());
        assertEquals("Start", pages.getFirst().title());
        assertEquals("Hallo", pages.getFirst().description());
        assertEquals(PageTarget.SUBMIT, pages.getFirst().after());
        assertEquals(List.of(), service.findQuestions(form));
    }

    @Test
    void savingQuestionsAloneKeepsThePages() {
        int form = newForm();
        service.saveLayout(form, List.of(page("p0", null), page("two", null)), List.of(text(null, "two", "A")));

        service.saveQuestions(form, List.of(text(null, null, "B")));

        assertEquals(List.of("p0", "two"), keys(service.findPages(form)));
        assertEquals("p0", service.findQuestions(form).getFirst().pageKey(), "no page named is the first page");
    }

    @Test
    void aPageLeadingUpwardsIsRefused() {
        int form = newForm();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.saveLayout(
                        form, List.of(page("p0", null), page("two", PageTarget.page("p0"))), List.of()));

        assertEquals(Refusal.FORM_PAGE_TARGET_NOT_FURTHER_DOWN, refused.refusal());
        assertEquals(List.of("p0"), keys(service.findPages(form)), "nothing was saved");
    }

    @Test
    void aPageLeadingToAPageTheFormDoesNotHaveIsRefused() {
        int form = newForm();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.saveLayout(form, List.of(page("p0", PageTarget.page("nowhere"))), List.of()));

        assertEquals(Refusal.FORM_PAGE_TARGET_NOT_FURTHER_DOWN, refused.refusal());
    }

    @Test
    void pagesNeedKeysOfTheirOwn() {
        int form = newForm();

        assertEquals(
                Refusal.FORM_PAGE_KEYS_NOT_DISTINCT,
                assertThrows(RefusalResponse.class, () -> service.saveLayout(form, List.of(), List.of()))
                        .refusal());
        assertEquals(
                Refusal.FORM_PAGE_KEYS_NOT_DISTINCT,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.saveLayout(form, List.of(page("a", null), page("a", null)), List.of()))
                        .refusal());
        assertEquals(
                Refusal.FORM_PAGE_KEYS_NOT_DISTINCT,
                assertThrows(RefusalResponse.class, () -> service.saveLayout(form, List.of(page(" ", null)), List.of()))
                        .refusal());
    }

    @Test
    void aQuestionOnAPageNotSentIsRefused() {
        int form = newForm();

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.saveLayout(form, List.of(page("p0", null)), List.of(text(null, "elsewhere", "A"))));

        assertEquals(Refusal.QUESTION_ON_NO_PAGE, refused.refusal());
    }

    private static int newForm() {
        return service.create(
                        station.id(), "Paged", "", false, true, false, null, null, member.id(), FormPurpose.INTERNAL)
                .id();
    }

    private static PageEntry page(String key, PageTarget after) {
        return new PageEntry(key, "", "", after);
    }

    private static QuestionEntry text(Integer id, String pageKey, String title) {
        return new QuestionEntry(
                id, pageKey, FormQuestionType.TEXT, title, "", false, false, new FormQuestionConfig.Text(false), null);
    }

    private static List<String> keys(List<FormPage> pages) {
        return pages.stream().map(FormPage::key).toList();
    }

    private static List<String> titles(List<FormQuestion> questions) {
        return questions.stream().map(FormQuestion::title).toList();
    }
}
