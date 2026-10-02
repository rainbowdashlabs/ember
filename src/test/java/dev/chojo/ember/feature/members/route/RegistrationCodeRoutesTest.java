/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.RegistrationCode;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.RegistrationCodeService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registration code routes over HTTP: a code addressed by its number is reached only from its
 * own station, and a code of another station answers as if it were not there and stays untouched.
 */
class RegistrationCodeRoutesTest extends RepositoryTestBase {
    private RouteHarness harness;
    private Station station;
    private Station elsewhere;
    private StationMember admin;
    private RegistrationCode own;
    private RegistrationCode foreign;
    private MemberGroup ownGroup;
    private MemberGroup foreignGroup;

    @BeforeEach
    void twoStations() {
        harness = RouteHarness.serving(new RegistrationCodeRoutes(
                        new RegistrationCodeService(registrationCodeRepo, newGroupMemberships())))
                .withStations(stationRepo);
        station = stationRepo.create("Code Routes " + System.nanoTime());
        elsewhere = stationRepo.create("Code Routes Elsewhere " + System.nanoTime());
        var account = accountRepo.create("code-routes-" + System.nanoTime() + "@test.com", "Co", "De");
        admin = stationMemberRepo.create(station.id(), account.id());
        own = registrationCodeRepo.create(station.id(), "OWN" + System.nanoTime(), 5);
        foreign = registrationCodeRepo.create(elsewhere.id(), "FOREIGN" + System.nanoTime(), 5);
        ownGroup = memberGroupRepo.create(station.id(), "Eigene");
        foreignGroup = memberGroupRepo.create(elsewhere.id(), "Fremde");
        registrationCodeRepo.addGroup(foreign.id(), foreignGroup.id());
    }

    @AfterEach
    void removeStations() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
    }

    private UserSession instanceAdmin() {
        return new UserSession(
                new Account(
                        admin.accountId(),
                        null,
                        "code-admin@test.com",
                        null,
                        "Co",
                        "De",
                        true,
                        null,
                        "Co De",
                        null,
                        null),
                1,
                station.id(),
                null,
                admin,
                Set.of(),
                Set.of(InstancePermission.ADMINISTRATOR),
                null);
    }

    private void foreignCodeIsUntouched() {
        assertTrue(registrationCodeRepo.findById(foreign.id()).isPresent(), "the other station's code still exists");
        assertEquals(List.of(foreignGroup.id()), registrationCodeRepo.findGroupIds(foreign.id()));
    }

    @Test
    void aCodeOfTheOwnStationIsReadAndItsGroupsAreChanged() {
        harness.run((server, client) -> {
            var session = harness.as(instanceAdmin());
            var read = client.get("/api/v1/registration-codes/" + own.id(), session);
            assertEquals(200, read.code());
            assertEquals(own.code(), json(read).path("code").asString());

            var set = client.put(
                    "/api/v1/registration-codes/" + own.id() + "/groups",
                    body("{\"groupIds\":[" + ownGroup.id() + "]}"),
                    session);
            assertEquals(200, set.code());
            var groups = client.get("/api/v1/registration-codes/" + own.id() + "/groups", session);
            assertEquals(ownGroup.id(), json(groups).get(0).asInt());

            assertEquals(
                    204,
                    client.delete("/api/v1/registration-codes/" + own.id(), null, session)
                            .code());
        });
        assertTrue(registrationCodeRepo.findById(own.id()).isEmpty());
    }

    @Test
    void aCodeOfAnotherStationIsNotRead() {
        harness.run((server, client) -> {
            var response = client.get("/api/v1/registration-codes/" + foreign.id(), harness.as(instanceAdmin()));
            assertEquals(MemberRefusal.REGISTRATION_CODE_NOT_HERE, refusalOf(response));
        });
        foreignCodeIsUntouched();
    }

    @Test
    void theGroupsOfACodeOfAnotherStationAreNotRead() {
        harness.run((server, client) -> {
            var response =
                    client.get("/api/v1/registration-codes/" + foreign.id() + "/groups", harness.as(instanceAdmin()));
            assertEquals(MemberRefusal.REGISTRATION_CODE_NOT_HERE_FOR_GROUPS, refusalOf(response));
        });
        foreignCodeIsUntouched();
    }

    @Test
    void theGroupsOfACodeOfAnotherStationAreNotChanged() {
        harness.run((server, client) -> {
            var response = client.put(
                    "/api/v1/registration-codes/" + foreign.id() + "/groups",
                    body("{\"groupIds\":[]}"),
                    harness.as(instanceAdmin()));
            assertEquals(MemberRefusal.REGISTRATION_CODE_NOT_HERE_TO_CHANGE_GROUPS, refusalOf(response));
        });
        foreignCodeIsUntouched();
    }

    @Test
    void aCodeOfAnotherStationIsNotDeleted() {
        harness.run((server, client) -> {
            var response =
                    client.delete("/api/v1/registration-codes/" + foreign.id(), null, harness.as(instanceAdmin()));
            assertEquals(MemberRefusal.REGISTRATION_CODE_NOT_DELETED, refusalOf(response));
        });
        foreignCodeIsUntouched();
    }
}
