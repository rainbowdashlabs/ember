/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger of expiry reminders done with, and finding every expiry date field across stations.
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

        repository.markSent(member.id(), field.id(), EXPIRES, List.of(first), SENT);
        repository.markSent(member.id(), field.id(), EXPIRES, List.of(first, second), SENT.plusSeconds(60));

        var sent = repository.findSent(field.id()).stream()
                .sorted((a, b) -> a.reminderDate().compareTo(b.reminderDate()))
                .toList();
        assertEquals(
                List.of(
                        new SentExpiryReminder(member.id(), EXPIRES, first, SENT),
                        new SentExpiryReminder(member.id(), EXPIRES, second, SENT.plusSeconds(60))),
                sent,
                "the first reminder keeps the moment it was first recorded");
    }

    @Test
    void aFieldGoneTakesItsLedgerWithIt() {
        var field = field("Führerschein gültig bis");
        repository.markSent(member.id(), field.id(), EXPIRES, List.of(EXPIRES), SENT);

        profileFieldRepo.delete(field.id());

        assertTrue(repository.findSent(field.id()).isEmpty());
    }

    @Test
    void everyStationsExpiryDatesAreFound() {
        var other = stationRepo.create("Expiry Reminder Other Station");
        var here = field("JuLeiCa Ablaufdatum");
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
}
