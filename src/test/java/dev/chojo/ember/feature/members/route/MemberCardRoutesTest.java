/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.MemberCard;
import dev.chojo.ember.feature.members.entity.MemberCard.MemberCardLabel;
import dev.chojo.ember.feature.members.service.MemberCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The member card over HTTP: anybody signed in at the station reads it, nobody reads a stranger's. */
class MemberCardRoutesTest {
    private static final int STATION_ID = 3;
    private static final UUID MEMBER_UID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private MemberCardService cardService;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        cardService = mock(MemberCardService.class);
        harness = RouteHarness.serving(new MemberCardRoutes(cardService));
    }

    @Test
    void anyMemberReadsTheCardWithoutAMemberPermission() {
        var identity = new MemberIdentity(UUID.randomUUID(), MEMBER_UID);
        when(cardService.find(any(), eq(MEMBER_UID)))
                .thenReturn(Optional.of(new MemberCard(
                        identity,
                        "Mara Nager",
                        false,
                        List.of(),
                        List.of(),
                        List.of(new MemberCardLabel("Medic", "#ff0000")),
                        List.of())));

        harness.run((server, client) -> {
            var body = json(client.get(
                    PREFIX + "/station-members/by-uid/" + MEMBER_UID + "/card",
                    harness.as(TestSessions.member(STATION_ID))));
            assertEquals("Mara Nager", body.get("name").asString());
            assertEquals("Medic", body.get("tags").get(0).get("name").asString());
        });
    }

    @Test
    void aUidWithoutACardIsNotFound() {
        when(cardService.find(any(), eq(MEMBER_UID))).thenReturn(Optional.empty());

        harness.run((server, client) -> assertEquals(
                MemberRefusal.MEMBER_NOT_HERE_BY_UID,
                refusalOf(client.get(
                        PREFIX + "/station-members/by-uid/" + MEMBER_UID + "/card",
                        harness.as(TestSessions.member(STATION_ID))))));
    }
}
