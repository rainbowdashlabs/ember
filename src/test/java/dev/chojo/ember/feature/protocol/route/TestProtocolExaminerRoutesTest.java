/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService;
import dev.chojo.ember.feature.protocol.service.TestProtocolGuards;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The examiner menu over HTTP: who it offers and what it calls them.
 */
class TestProtocolExaminerRoutesTest {
    private static final int STATION = 3;

    /**
     * A member with an account carries no name on the membership, so the menu has to ask for the name
     * the station reads; read from the membership alone, every option came out blank.
     */
    @Test
    void theMenuNamesEveryCandidateAndSortsThemByName() {
        var examiners = mock(TestProtocolExaminerService.class);
        var names = mock(MemberNameResolver.class);
        when(examiners.candidates(STATION)).thenReturn(List.of(member(7), member(8)));
        when(names.identified(7)).thenReturn("Zoe Weber");
        when(names.identified(8)).thenReturn("Anna Kraus");
        var harness = RouteHarness.serving(new TestProtocolExaminerRoutes(
                examiners, new TestProtocolGuards(mock(TestProtocolService.class)), names));

        var answer = harness.request(client -> client.get(
                PREFIX + "/protocols/examiner-candidates",
                harness.as(TestSessions.member(STATION, StationPermission.PROTOCOL_CREATE))));

        assertEquals(200, answer.code());
        assertEquals(
                "[{\"memberId\":8,\"name\":\"Anna Kraus\"},{\"memberId\":7,\"name\":\"Zoe Weber\"}]",
                answer.body().string());
    }

    private static StationMember member(int id) {
        return new StationMember(
                id,
                STATION,
                UUID.randomUUID(),
                id,
                false,
                null,
                "",
                StationUserType.TEAM,
                LocalDate.of(2026, 1, 1),
                null);
    }
}
