/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileFieldRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int fieldId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Profile Station");
        account = accountRepo.create("profile@test.com", "Profile", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        ProfileField field = profileFieldRepo.create(
                station.id(),
                "Phone",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                1,
                ProfileFieldScope.MEMBER);
        assertNotNull(field);
        assertEquals("Phone", field.name());
        assertEquals(1, field.position());
        fieldId = field.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(profileFieldRepo.findById(fieldId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        assertEquals(1, profileFieldRepo.findByStation(station.id()).size());
    }

    @Test
    @Order(4)
    void findByStationAndScope() {
        assertEquals(
                1,
                profileFieldRepo
                        .findByStationAndScope(station.id(), ProfileFieldScope.MEMBER)
                        .size());
        assertTrue(profileFieldRepo
                .findByStationAndScope(station.id(), ProfileFieldScope.TEAM)
                .isEmpty());
    }

    @Test
    @Order(5)
    void update() {
        assertTrue(profileFieldRepo.update(
                fieldId, "Email", ProfileFieldType.TEXT, ProfileFieldConfig.parse("{}"), 2, false));
        ProfileField updated = profileFieldRepo.findById(fieldId).orElseThrow();
        assertEquals("Email", updated.name());
        assertEquals(2, updated.position());
    }

    // -- Values --

    @Test
    @Order(10)
    void setAndFindValue() {
        profileFieldRepo.setValue(member.id(), fieldId, "\"test@test.com\"");
        var values = profileFieldRepo.findValues(member.id());
        assertEquals(1, values.size());
        assertEquals("\"test@test.com\"", values.getFirst().value());
    }

    @Test
    @Order(11)
    void findValue() {
        assertTrue(profileFieldRepo.findValue(member.id(), fieldId).isPresent());
        assertTrue(profileFieldRepo.findValue(member.id(), 99999).isEmpty());
    }

    @Test
    @Order(12)
    void upsertValue() {
        profileFieldRepo.setValue(member.id(), fieldId, "\"updated@test.com\"");
        assertEquals(
                "\"updated@test.com\"",
                profileFieldRepo.findValue(member.id(), fieldId).orElseThrow().value());
    }

    @Test
    @Order(13)
    void deleteValue() {
        assertTrue(profileFieldRepo.deleteValue(member.id(), fieldId));
        assertTrue(profileFieldRepo.findValue(member.id(), fieldId).isEmpty());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(profileFieldRepo.delete(fieldId));
        assertTrue(profileFieldRepo.findById(fieldId).isEmpty());
    }
}
