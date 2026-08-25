/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizQuestionReportRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int catalogId;
    private static int questionId;
    private static int reportId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("QuizReportRepoStation");
        account = accountRepo.create("quizreport-repo@test.com", "Quiz", "Reporter");
        member = stationMemberRepo.create(station.id(), account.id());

        catalogId = quizCatalogRepo
                .create(station.id(), "ReportRepoCatalog", "", true, CatalogMetadata.none())
                .id();
        questionId = quizCatalogRepo
                .createQuestion(
                        catalogId,
                        null,
                        QuizQuestionType.TRUE_FALSE,
                        "Is the hydrant red?",
                        "",
                        null,
                        1.0,
                        false,
                        "{\"correctAnswer\":true}",
                        0)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createNamesTheReporter() {
        var report = quizQuestionReportRepo.create(questionId, member.id(), "Die Antwort stimmt nicht.");

        assertEquals(questionId, report.questionId());
        assertEquals("Die Antwort stimmt nicht.", report.note());
        assertEquals(member.displayName(), report.reporterName());
        reportId = report.id();
    }

    /** A note whose author has left the station keeps the note and loses only the name. */
    @Test
    @Order(2)
    void createWithoutAMemberIsAnonymous() {
        var report = quizQuestionReportRepo.create(questionId, null, "Anonym gemeldet.");

        assertEquals("", report.reporterName());
        assertTrue(quizQuestionReportRepo.delete(report.id()));
    }

    @Test
    @Order(3)
    void findByCatalogReadsTheOpenNotes() {
        var reports = quizQuestionReportRepo.findByCatalog(catalogId);

        assertEquals(1, reports.size());
        assertEquals(reportId, reports.getFirst().id());
    }

    @Test
    @Order(4)
    void findCatalogOfReportAnswersThePermissionQuestion() {
        assertEquals(
                catalogId, quizQuestionReportRepo.findCatalogOfReport(reportId).orElseThrow());
        assertTrue(quizQuestionReportRepo.findCatalogOfReport(reportId + 10_000).isEmpty());
    }

    @Test
    @Order(5)
    void deleteRemovesTheNote() {
        assertTrue(quizQuestionReportRepo.delete(reportId));
        assertFalse(quizQuestionReportRepo.delete(reportId));
        assertTrue(quizQuestionReportRepo.findByCatalog(catalogId).isEmpty());
    }
}
