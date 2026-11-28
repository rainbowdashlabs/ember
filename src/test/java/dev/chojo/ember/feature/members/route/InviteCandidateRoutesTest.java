/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.FindableBecause;
import dev.chojo.ember.feature.members.entity.InviteCandidate;
import dev.chojo.ember.feature.members.repository.InviteCandidateRepository;
import dev.chojo.ember.feature.members.service.InviteCandidateRateLimiter;
import dev.chojo.ember.feature.members.service.InviteCandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Searching for people to invite by name, over HTTP: only whoever may invite members searches, a search
 * needs a word of three letters and is bounded per account, and an answer carries the name and the tie but never
 * the address or the account's number.
 */
class InviteCandidateRoutesTest {
    private static final String SEARCH = PREFIX + "/members/invite-candidates";
    private static final int STATION = 3;

    private InviteCandidateRepository repository;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        repository = mock(InviteCandidateRepository.class);
        var limiter =
                new InviteCandidateRateLimiter(Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC));
        harness = RouteHarness.serving(new InviteCandidateRoutes(new InviteCandidateService(repository, limiter)));
    }

    private static InviteCandidate candidate() {
        return new InviteCandidate(
                77,
                UUID.fromString("00000000-0000-0000-0000-000000000077"),
                "Anna",
                "Schmidt",
                FindableBecause.SHARED_STATION,
                "Wache Nord",
                false,
                false,
                false);
    }

    @Test
    void aManagerFindsNamesAndTiesButNoAddressesOrNumbers() {
        var manager = harness.as(TestSessions.member(STATION, StationPermission.MEMBER_EDIT));
        when(repository.find(TestSessions.ACCOUNT_ID, STATION, List.of("anna", "schm"), 10))
                .thenReturn(List.of(candidate()));

        var response = harness.request(client -> client.get(SEARCH + "?q=%20anna%20%20schm%20", manager));

        assertEquals(200, response.code());
        var found = json(response).get(0);
        assertEquals(
                "00000000-0000-0000-0000-000000000077", found.get("accountUid").asText());
        assertEquals("SHARED_STATION", found.get("because").asText());
        assertEquals("Wache Nord", found.get("through").asText());
        assertFalse(found.has("accountId"));
        assertFalse(found.has("email"));
    }

    @Test
    void somebodyWhoMayNotInviteCannotSearch() {
        var member = harness.as(TestSessions.member(STATION, StationPermission.LOGIN));

        var response = harness.request(client -> client.get(SEARCH + "?q=anna", member));

        assertEquals(403, response.code());
        verify(repository, never()).find(anyInt(), anyInt(), anyList(), anyInt());
    }

    @Test
    void aSearchWithoutAWordOfThreeLettersIsRefused() {
        var manager = harness.as(TestSessions.member(STATION, StationPermission.MEMBER_EDIT));

        var short1 = harness.request(client -> client.get(SEARCH + "?q=an", manager));
        var letters = harness.request(client -> client.get(SEARCH + "?q=a%20e%20n", manager));
        var none = harness.request(client -> client.get(SEARCH, manager));

        assertEquals(MemberRefusal.INVITE_SEARCH_TOO_SHORT, refusalOf(short1));
        assertEquals(MemberRefusal.INVITE_SEARCH_TOO_SHORT, refusalOf(letters), "single letters do not add up");
        assertEquals(MemberRefusal.INVITE_SEARCH_TOO_SHORT, refusalOf(none));
        verify(repository, never()).find(anyInt(), anyInt(), anyList(), anyInt());
    }

    @Test
    void searchingOnAndOnIsRefusedOnceTheBucketIsEmpty() {
        var manager = harness.as(TestSessions.member(STATION, StationPermission.MEMBER_EDIT));
        when(repository.find(eq(TestSessions.ACCOUNT_ID), eq(STATION), anyList(), eq(10)))
                .thenReturn(List.of());

        harness.run((server, client) -> {
            for (int i = 0; i < InviteCandidateRateLimiter.CAPACITY; i++) {
                assertEquals(200, client.get(SEARCH + "?q=anna", manager).code());
            }
            var refused = client.get(SEARCH + "?q=anna", manager);
            assertEquals(429, refused.code());
            assertEquals(MemberRefusal.INVITE_SEARCHING_TOO_OFTEN, refusalOf(refused));
        });
    }
}
