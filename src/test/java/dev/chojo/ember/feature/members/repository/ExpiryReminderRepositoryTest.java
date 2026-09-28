/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.SentExpiryReminder;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger of expiry reminders done with, and finding every expiry date field and its answers
 * across stations and associations.
 */
class ExpiryReminderRepositoryTest extends RepositoryTestBase {
    private static final LocalDate EXPIRES = LocalDate.of(2027, 3, 31);
    private static final Instant SENT = Instant.parse("2027-03-01T08:00:00Z");

    private static final ExpiryReminderRepository repository = new ExpiryReminderRepository();
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Expiry Reminder Station");
        account = accountRepo.create("expiry-ledger@test.com", "Erika", "Ablauf");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static ProfileField field(String name) {
        return profileFieldRepo.create(
                station.id(), name, ProfileFieldType.EXPIRY_DATE, ProfileFieldConfig.empty(), false, false, null);
    }

    @Test
    void aReminderIsRecordedOnceByTheDayItWasDue() {
        var field = field("Erste Hilfe gültig bis");
        var first = LocalDate.of(2027, 3, 1);
        var second = LocalDate.of(2027, 3, 24);

        repository.markSent(member.id(), FieldOrigin.STATION, field.id(), EXPIRES, List.of(first), SENT);
        repository.markSent(
                member.id(), FieldOrigin.STATION, field.id(), EXPIRES, List.of(first, second), SENT.plusSeconds(60));

        var sent = repository.findSent(FieldOrigin.STATION, field.id()).stream()
                .sorted(Comparator.comparing(SentExpiryReminder::reminderDate))
                .toList();
        assertEquals(
                List.of(
                        new SentExpiryReminder(member.id(), EXPIRES, first, SENT),
                        new SentExpiryReminder(member.id(), EXPIRES, second, SENT.plusSeconds(60))),
                sent,
                "the first reminder keeps the moment it was first recorded");
    }

    /** A station's field and an association's field may carry the same number and are still two fields. */
    @Test
    void theSameNumberUnderTwoOriginsIsTwoFields() {
        var field = field("Führerschein gültig bis");
        repository.markSent(member.id(), FieldOrigin.STATION, field.id(), EXPIRES, List.of(EXPIRES), SENT);

        assertEquals(1, repository.findSent(FieldOrigin.STATION, field.id()).size());
        assertTrue(repository.findSent(FieldOrigin.CLUSTER, field.id()).isEmpty());
    }

    @Test
    void theRecordsOfADeletedFieldAreForgotten() {
        var kept = field("JuLeiCa Ablaufdatum");
        var gone = field("Atemschutz gültig bis");
        repository.markSent(member.id(), FieldOrigin.STATION, kept.id(), EXPIRES, List.of(EXPIRES), SENT);
        repository.markSent(member.id(), FieldOrigin.STATION, gone.id(), EXPIRES, List.of(EXPIRES), SENT);
        repository.markSent(member.id(), FieldOrigin.CLUSTER, Integer.MAX_VALUE, EXPIRES, List.of(EXPIRES), SENT);
        profileFieldRepo.delete(gone.id());

        assertTrue(repository.forgetDeletedFields() >= 2);

        assertTrue(repository.findSent(FieldOrigin.STATION, gone.id()).isEmpty());
        assertTrue(repository.findSent(FieldOrigin.CLUSTER, Integer.MAX_VALUE).isEmpty());
        assertEquals(1, repository.findSent(FieldOrigin.STATION, kept.id()).size());
    }

    @Test
    void everyStationsExpiryDatesAreFound() {
        var other = stationRepo.create("Expiry Reminder Other Station");
        var here = field("Sanitätsdienst gültig bis");
        var there = profileFieldRepo.create(
                other.id(),
                "Atemschutz gültig bis",
                ProfileFieldType.EXPIRY_DATE,
                ProfileFieldConfig.empty(),
                false,
                false,
                null);
        var plain = profileFieldRepo.create(
                station.id(), "Beitrittsdatum", ProfileFieldType.DATE, ProfileFieldConfig.empty(), false, false, null);

        var found = profileFieldRepo.findAllByType(ProfileFieldType.EXPIRY_DATE).stream()
                .map(ProfileField::id)
                .toList();

        assertTrue(found.contains(here.id()));
        assertTrue(found.contains(there.id()));
        assertFalse(found.contains(plain.id()));
        stationRepo.delete(other.id());
    }

    @Test
    void everyAssociationsExpiryDatesAndTheirAnswersAreFound() {
        var cluster = clusterService.create("Expiry Reminder Association " + System.nanoTime(), null);
        var expiring = clusterProfileFieldRepo.create(
                cluster.id(),
                "Maschinist gültig bis",
                ProfileFieldType.EXPIRY_DATE,
                ProfileFieldConfig.empty(),
                false,
                false,
                null,
                false,
                false,
                null);
        clusterProfileFieldRepo.setValue(member.id(), expiring.id(), StringNode.valueOf("2027-03-31"));

        var found = clusterProfileFieldRepo.findAllByType(ProfileFieldType.EXPIRY_DATE).stream()
                .map(field -> field.id())
                .toList();
        var answers = clusterProfileFieldRepo.findValuesOfField(expiring.id());

        assertTrue(found.contains(expiring.id()));
        assertEquals(1, answers.size());
        assertEquals(member.id(), answers.getFirst().memberId());
        assertEquals("2027-03-31", answers.getFirst().plainValue());
    }
}
