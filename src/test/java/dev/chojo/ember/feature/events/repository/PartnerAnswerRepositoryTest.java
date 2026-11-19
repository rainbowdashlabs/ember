/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.events.entity.DateSpan;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The answers of partners' members are kept per partner registration and read back per appointment. */
class PartnerAnswerRepositoryTest extends RepositoryTestBase {
    private static final PartnerAnswerRepository answers = new PartnerAnswerRepository();
    private static final EventRegistrationFieldRepository questions = new EventRegistrationFieldRepository();
    private static final LocalDate DATE = LocalDate.of(2028, 9, 4);

    private static Station station;
    private static Station other;
    private static int partnerId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Partner Answers Station");
        other = stationRepo.create("Partner Answers Partner");
        partnerId = query("""
                        INSERT INTO federation_partner (station_id, partner_station_id, status, remote_host, cluster_managed)
                        VALUES (:station, :partner::UUID, 'ACTIVE', NULL, FALSE)
                        RETURNING id;""")
                .single(call().bind("station", station.id())
                        .bind("partner", other.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(other.id());
    }

    private static StationEvent event() {
        var start = Instant.parse("2028-09-04T16:00:00Z");
        return eventRepo.create(
                station.id(),
                "Lehrgang",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plusSeconds(7200),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
    }

    private static int question(StationEvent event) {
        questions.replaceFields(
                event.id(),
                List.of(new RegistrationFieldDraft("Größe", FieldType.TEXT, EventQuestionSettings.empty(), true)));
        return questions.findByEvent(event.id()).getFirst().id();
    }

    private static int partnerSignUp(StationEvent event) {
        return new EventFederationRepository()
                .createRegistration(event.id(), partnerId, UUID.randomUUID(), DATE, RegistrationStatus.PENDING)
                .id();
    }

    @Test
    void answersAreReadBackPerRegistrationAndReplacedByANewAnswer() {
        var event = event();
        try {
            int field = question(event);
            int first = partnerSignUp(event);
            int second = partnerSignUp(event);

            answers.setAnswers(first, Map.of(field, "M"));
            answers.setAnswers(first, Map.of(field, "L"));
            answers.setAnswers(second, Map.of(field, "S"));

            assertEquals(
                    List.of(
                            new RegistrationFieldValue(first, field, "L"),
                            new RegistrationFieldValue(second, field, "S")),
                    answers.findForEvent(station.id(), event.id()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /** A registration taken up again by a new sign-up keeps no answer of the old one it left open. */
    @Test
    void writingAgainDropsTheAnswersLeftOpen() {
        var event = event();
        try {
            questions.replaceFields(
                    event.id(),
                    List.of(
                            new RegistrationFieldDraft("Größe", FieldType.TEXT, EventQuestionSettings.empty(), true),
                            new RegistrationFieldDraft("Essen", FieldType.TEXT, EventQuestionSettings.empty(), true)));
            var asked = questions.findByEvent(event.id());
            int size = asked.get(0).id();
            int food = asked.get(1).id();
            int signUp = partnerSignUp(event);

            answers.setAnswers(signUp, Map.of(size, "M", food, "Vegetarisch"));
            answers.setAnswers(signUp, Map.of(size, "L"));

            assertEquals(
                    List.of(new RegistrationFieldValue(signUp, size, "L")),
                    answers.findForEvent(station.id(), event.id()));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /** A date handed to another appointment takes its partners' answers along, to the copied questions. */
    @Test
    void aMovedDateTakesItsPartnerAnswersToTheCopiedQuestions() {
        var source = event();
        var target = event();
        try {
            int field = question(source);
            int signUp = partnerSignUp(source);
            answers.setAnswers(signUp, Map.of(field, "M"));
            var handover = new EventHandoverRepository();
            handover.copyConfiguration(source.id(), target.id());

            handover.moveDates(source.id(), target.id(), new DateSpan(DATE, DATE));

            int copied = questions.findByEvent(target.id()).getFirst().id();
            assertEquals(
                    List.of(new RegistrationFieldValue(signUp, copied, "M")),
                    answers.findForEvent(station.id(), target.id()));
            assertTrue(answers.findForEvent(station.id(), source.id()).isEmpty());
        } finally {
            eventRepo.delete(source.id());
            eventRepo.delete(target.id());
        }
    }

    @Test
    void anotherStationReadsNothing() {
        var event = event();
        try {
            int field = question(event);
            answers.setAnswers(partnerSignUp(event), Map.of(field, "M"));

            assertTrue(answers.findForEvent(other.id(), event.id()).isEmpty());
        } finally {
            eventRepo.delete(event.id());
        }
    }
}
