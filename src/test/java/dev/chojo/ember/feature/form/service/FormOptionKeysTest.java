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
import dev.chojo.ember.feature.form.entity.FormQuestionConfig.Option;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Answers name options by key, so changing the options of a question changes only what was changed:
 * reordering and renaming keep every answer, and removing an option drops only its selections.
 */
class FormOptionKeysTest extends RepositoryTestBase {
    private static final Option YES = new Option("k-yes", "Ja");
    private static final Option NO = new Option("k-no", "Nein");
    private static final Option MAYBE = new Option("k-maybe", "Vielleicht");

    private static FormService service;
    private static Station station;
    private static final List<Account> accounts = new ArrayList<>();
    private static final List<StationMember> members = new ArrayList<>();

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
        station = stationRepo.create("FormOptionKeysStation");
        for (int i = 0; i < 3; i++) {
            var account = accountRepo.create("form-option-keys-" + i + "@test.com", "Form", "Keys" + i);
            accounts.add(account);
            members.add(stationMemberRepo.create(station.id(), account.id()));
        }
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    @Test
    void reorderingAndRenamingOptionsKeepsWhatWasChosen() {
        var question = questionWith(choice(YES, NO, MAYBE, true));
        answer(question, 0, new FormAnswerValue.Choice(List.of("k-no"), null));

        save(question, choice(new Option("k-maybe", "Weiß nicht"), NO, new Option("k-yes", "Ja, gern"), true));

        assertEquals(List.of(new FormAnswerValue.Choice(List.of("k-no"), null)), answersTo(question));
        var tally = FormResultTally.tally(
                        service.findQuestions(question.formId()),
                        service.findAllAnswersForForm(question.formId()),
                        null)
                .getFirst();
        assertEquals(
                List.of("k-maybe", "k-no", "k-yes"),
                List.copyOf(tally.optionCounts().keySet()));
        assertEquals(1, tally.optionCounts().get("k-no"));
        assertEquals(0, tally.optionCounts().get("k-yes"));
    }

    @Test
    void removingAChoiceOptionDropsOnlyItsSelections() {
        var question = questionWith(choice(YES, NO, MAYBE, true));
        answer(question, 0, new FormAnswerValue.Choice(List.of("k-yes", "k-no"), null));
        answer(question, 1, new FormAnswerValue.Choice(List.of("k-no"), null));
        answer(question, 2, new FormAnswerValue.Choice(List.of("k-maybe"), null));

        save(question, choice(YES, MAYBE, true));

        assertEquals(
                List.of(
                        new FormAnswerValue.Choice(List.of("k-yes"), null),
                        new FormAnswerValue.Choice(List.of("k-maybe"), null)),
                answersTo(question),
                "a choice left with nothing selected is gone, the others lose only the removed option");
        assertEquals(3, service.countResponses(question.formId()), "the responses themselves stay");
    }

    @Test
    void aChoiceLeftWithOnlyItsOtherTextStays() {
        var question = questionWith(choice(YES, NO, MAYBE, true));
        answer(question, 0, new FormAnswerValue.Choice(List.of("k-no"), "Übungsdienst"));

        save(question, choice(YES, MAYBE, true));

        assertEquals(List.of(new FormAnswerValue.Choice(List.of(), "Übungsdienst")), answersTo(question));
    }

    @Test
    void removingARankingOptionDropsItFromTheOrder() {
        var question = questionWith(new FormQuestionConfig.Ranking(List.of(YES, NO, MAYBE)));
        answer(question, 0, new FormAnswerValue.Ranking(List.of("k-maybe", "k-yes", "k-no")));

        save(question, new FormQuestionConfig.Ranking(List.of(MAYBE, NO)));

        assertEquals(List.of(new FormAnswerValue.Ranking(List.of("k-maybe", "k-no"))), answersTo(question));
    }

    @Test
    void removingALikertStatementDropsItsRating() {
        var question = questionWith(new FormQuestionConfig.Likert(List.of(YES, NO), 1, 5, List.of()));
        answer(question, 0, new FormAnswerValue.Likert(Map.of("k-yes", 4, "k-no", 2)));
        answer(question, 1, new FormAnswerValue.Likert(Map.of("k-no", 5)));

        save(question, new FormQuestionConfig.Likert(List.of(YES), 1, 5, List.of()));

        assertEquals(List.of(new FormAnswerValue.Likert(Map.of("k-yes", 4))), answersTo(question));
    }

    @Test
    void countsTheAnswersNamingEachOption() {
        var question = questionWith(choice(YES, NO, MAYBE, true));
        answer(question, 0, new FormAnswerValue.Choice(List.of("k-yes", "k-no"), null));
        answer(question, 1, new FormAnswerValue.Choice(List.of("k-no"), null));

        var count = service.countAnswersPerQuestion(question.formId()).getFirst();

        assertEquals(2, count.answers());
        assertEquals(Map.of("k-yes", 1, "k-no", 2, "k-maybe", 0), count.optionAnswers());
    }

    @Test
    void optionsWithoutADistinctKeyAreRefused() {
        var question = questionWith(choice(YES, NO, false));

        var duplicate = assertThrows(
                RefusalResponse.class, () -> save(question, choice(YES, new Option("k-yes", "Nein"), false)));
        var missing =
                assertThrows(RefusalResponse.class, () -> save(question, choice(YES, new Option(" ", "Nein"), false)));

        assertEquals(Refusal.QUESTION_OPTION_KEYS_NOT_DISTINCT, duplicate.refusal());
        assertEquals(Refusal.QUESTION_OPTION_KEYS_NOT_DISTINCT, missing.refusal());
        assertEquals(
                choice(YES, NO, false),
                service.findQuestions(question.formId()).getFirst().config());
    }

    @Test
    void anAnswerNamingAnUnknownOptionIsRefused() {
        var question = questionWith(choice(YES, NO, false));

        assertThrows(
                RuntimeException.class,
                () -> service.submitResponse(
                        question.formId(),
                        members.getFirst().id(),
                        members.getFirst().id(),
                        Map.of(question.id(), new FormAnswerValue.Choice(List.of("k-gone"), null))));
    }

    private static FormQuestionConfig.Choice choice(Option first, Option second, boolean multi) {
        return new FormQuestionConfig.Choice(
                List.of(first, second), multi, false, true, FormQuestionConfig.MultiLimitType.NONE, null);
    }

    private static FormQuestionConfig.Choice choice(Option first, Option second, Option third, boolean multi) {
        return new FormQuestionConfig.Choice(
                List.of(first, second, third), multi, false, true, FormQuestionConfig.MultiLimitType.NONE, null);
    }

    private static FormQuestion questionWith(FormQuestionConfig config) {
        var form = service.create(
                station.id(),
                "Options",
                "",
                false,
                true,
                false,
                null,
                null,
                members.getFirst().id(),
                FormPurpose.INTERNAL);
        service.saveQuestions(
                form.id(), List.of(new QuestionEntry(null, typeOf(config), "Frage", "", false, false, config)));
        return service.findQuestions(form.id()).getFirst();
    }

    private static void save(FormQuestion question, FormQuestionConfig config) {
        service.saveQuestions(
                question.formId(),
                List.of(new QuestionEntry(question.id(), typeOf(config), "Frage", "", false, false, config)));
    }

    private static void answer(FormQuestion question, int respondent, FormAnswerValue value) {
        var member = members.get(respondent);
        service.submitResponse(question.formId(), member.id(), member.id(), Map.of(question.id(), value));
    }

    private static List<FormAnswerValue> answersTo(FormQuestion question) {
        return service.findAllAnswersForForm(question.formId()).stream()
                .filter(answer -> answer.questionId() == question.id())
                .sorted(Comparator.comparingInt(FormAnswer::id))
                .map(answer -> FormAnswerValue.parse(question.formQuestionType(), answer.value()))
                .toList();
    }

    private static FormQuestionType typeOf(FormQuestionConfig config) {
        return switch (config) {
            case FormQuestionConfig.Choice c -> FormQuestionType.CHOICE;
            case FormQuestionConfig.Ranking r -> FormQuestionType.RANKING;
            case FormQuestionConfig.Likert l -> FormQuestionType.LIKERT;
            default -> throw new IllegalArgumentException("Not a question with options: " + config);
        };
    }
}
