/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationMailConfig;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationMailConfigRepositoryTest extends RepositoryTestBase {
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("MailConfig Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void findByStationEmpty() {
        assertTrue(stationMailConfigRepo.findByStation(station.id()).isEmpty());
    }

    @Test
    @Order(2)
    void upsert() {
        var config = new StationMailConfig(
                station.id(),
                MailProviderType.SMTP,
                "mail.example.com",
                587,
                true,
                "user",
                "pass",
                "noreply@example.com",
                "Ember",
                "",
                "",
                "",
                100,
                3000);
        var result = stationMailConfigRepo.upsert(config);
        assertNotNull(result);
        assertEquals(MailProviderType.SMTP, result.provider());
        assertEquals("mail.example.com", result.smtpHost());
        assertEquals(587, result.smtpPort());
    }

    @Test
    @Order(3)
    void findByStation() {
        var config = stationMailConfigRepo.findByStation(station.id());
        assertTrue(config.isPresent());
        assertEquals("mail.example.com", config.get().smtpHost());
    }

    @Test
    @Order(4)
    void upsertUpdate() {
        var config = new StationMailConfig(
                station.id(),
                MailProviderType.BREVO,
                "",
                0,
                false,
                "",
                "",
                "noreply@brevo.com",
                "Ember Brevo",
                "api-key-123",
                "Brevo",
                "https://api.brevo.com",
                200,
                5000);
        var result = stationMailConfigRepo.upsert(config);
        assertEquals(MailProviderType.BREVO, result.provider());
        assertEquals("api-key-123", result.apiKey());
    }

    // -- Daily/Monthly counts --

    @Test
    @Order(10)
    void getDailyCountEmpty() {
        assertEquals(0, stationMailConfigRepo.getDailyCount(station.id(), LocalDate.now()));
    }

    @Test
    @Order(11)
    void incrementDailyCount() {
        LocalDate today = LocalDate.now();
        stationMailConfigRepo.incrementDailyCount(station.id(), today);
        assertEquals(1, stationMailConfigRepo.getDailyCount(station.id(), today));
        stationMailConfigRepo.incrementDailyCount(station.id(), today);
        assertEquals(2, stationMailConfigRepo.getDailyCount(station.id(), today));
    }

    @Test
    @Order(12)
    void getMonthlyCount() {
        int monthly = stationMailConfigRepo.getMonthlyCount(station.id(), LocalDate.now());
        assertTrue(monthly >= 2);
    }

    @Test
    @Order(13)
    void cleanupOldCounts() {
        // Should not throw
        stationMailConfigRepo.cleanupOldCounts(1);
    }

    @Test
    @Order(99)
    void delete() {
        stationMailConfigRepo.delete(station.id());
        assertTrue(stationMailConfigRepo.findByStation(station.id()).isEmpty());
    }
}
