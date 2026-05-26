/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.form.entity.QuestionType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FormRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int formId;
    private static int questionId;
    private static int responseId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Form Station");
        account = accountRepo.create("form@test.com", "Form", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- Forms --

    @Test
    @Order(1)
    void create() {
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-01T00:00:00Z");
        Form form = formRepo.create(station.id(), "Test Form", "A test form", false, true, start, end, member.id());
        assertNotNull(form);
        assertEquals("Test Form", form.title());
        assertEquals(Form.FormStatus.DRAFT, form.status());
        formId = form.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(formRepo.findById(formId).isPresent());
        assertTrue(formRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        var forms = formRepo.findByStation(station.id());
        assertEquals(1, forms.size());
        assertEquals("Test Form", forms.getFirst().title());
    }

    @Test
    @Order(4)
    void update() {
        Instant start = Instant.parse("2026-06-15T00:00:00Z");
        Instant end = Instant.parse("2026-08-01T00:00:00Z");
        assertTrue(formRepo.update(formId, "Updated Form", "Updated desc", true, false, start, end));
        Form updated = formRepo.findById(formId).orElseThrow();
        assertEquals("Updated Form", updated.title());
        assertTrue(updated.shuffleQuestions());
        assertFalse(updated.allowEdit());
    }

    @Test
    @Order(5)
    void updateStatus() {
        assertTrue(formRepo.updateStatus(formId, Form.FormStatus.OPEN));
        assertEquals(
                Form.FormStatus.OPEN, formRepo.findById(formId).orElseThrow().status());
    }

    // -- Questions --

    @Test
    @Order(10)
    void createQuestion() {
        FormQuestion q = formRepo.createQuestion(
                formId,
                0,
                QuestionType.TEXT,
                "Your name?",
                "Enter name",
                true,
                false,
                new FormQuestionConfig.Text(false));
        assertNotNull(q);
        assertEquals("Your name?", q.title());
        assertEquals(QuestionType.TEXT, q.questionType());
        questionId = q.id();
    }

    @Test
    @Order(11)
    void findQuestions() {
        var questions = formRepo.findQuestions(formId);
        assertEquals(1, questions.size());
    }

    @Test
    @Order(12)
    void updateQuestion() {
        assertTrue(formRepo.updateQuestion(
                questionId, "Full name?", "Enter full name", false, true, new FormQuestionConfig.Text(false), 1));
        var questions = formRepo.findQuestions(formId);
        assertEquals("Full name?", questions.getFirst().title());
        assertEquals(1, questions.getFirst().position());
    }

    // -- Responses --

    @Test
    @Order(20)
    void createResponse() {
        FormResponse resp = formRepo.createResponse(formId, member.id(), member.id());
        assertNotNull(resp);
        assertEquals(formId, resp.formId());
        assertEquals(member.id(), resp.memberId());
        responseId = resp.id();
    }

    @Test
    @Order(21)
    void findResponse() {
        assertTrue(formRepo.findResponse(formId, member.id()).isPresent());
        assertTrue(formRepo.findResponse(formId, 99999).isEmpty());
    }

    @Test
    @Order(22)
    void findResponses() {
        assertEquals(1, formRepo.findResponses(formId).size());
    }

    @Test
    @Order(23)
    void countResponses() {
        assertEquals(1, formRepo.countResponses(formId));
    }

    @Test
    @Order(24)
    void hasResponded() {
        assertTrue(formRepo.hasResponded(formId, member.id()));
        assertFalse(formRepo.hasResponded(formId, 99999));
    }

    // -- Answers --

    @Test
    @Order(30)
    void upsertAnswer() {
        formRepo.upsertAnswer(responseId, questionId, new FormAnswerValue.Text("John Doe"));
        var answers = formRepo.findAnswers(responseId);
        assertEquals(1, answers.size());
    }

    @Test
    @Order(31)
    void findAllAnswersForQuestion() {
        var answers = formRepo.findAllAnswersForQuestion(questionId);
        assertEquals(1, answers.size());
    }

    @Test
    @Order(32)
    void upsertAnswerOverwrite() {
        formRepo.upsertAnswer(responseId, questionId, new FormAnswerValue.Text("Jane Doe"));
        var answers = formRepo.findAnswers(responseId);
        assertEquals(1, answers.size());
    }

    // -- Restrictions (now handled by RestrictionRepository) --

    @Test
    @Order(40)
    void setAndFindRestrictions() {
        var restrictionRepo = new RestrictionRepository();
        restrictionRepo.setRestrictions(
                "form_restriction", "form_id", formId, List.of(1, 2), List.of(), List.of(), List.of());
        var restrictions = restrictionRepo.findRestrictions("form_restriction", "form_id", formId);
        assertEquals(2, restrictions.size());
        // Clear
        restrictionRepo.setRestrictions(
                "form_restriction", "form_id", formId, List.of(), List.of(), List.of(), List.of());
        assertTrue(restrictionRepo
                .findRestrictions("form_restriction", "form_id", formId)
                .isEmpty());
    }

    // -- Cleanup --

    @Test
    @Order(90)
    void deleteResponse() {
        assertTrue(formRepo.deleteResponse(responseId));
        assertEquals(0, formRepo.countResponses(formId));
    }

    @Test
    @Order(91)
    void deleteQuestion() {
        assertTrue(formRepo.deleteQuestion(questionId));
        assertTrue(formRepo.findQuestions(formId).isEmpty());
    }

    @Test
    @Order(92)
    void updateStatusClosed() {
        assertTrue(formRepo.updateStatus(formId, Form.FormStatus.CLOSED));
        assertEquals(
                Form.FormStatus.CLOSED, formRepo.findById(formId).orElseThrow().status());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(formRepo.delete(formId));
        assertTrue(formRepo.findById(formId).isEmpty());
    }
}
