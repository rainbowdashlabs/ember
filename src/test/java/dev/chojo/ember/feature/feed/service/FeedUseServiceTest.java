/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.feed.entity.FeedUse;
import dev.chojo.ember.feature.feed.repository.FeedTokenRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedUseServiceTest {
    @Test
    void eachSubscriptionCarriesTheMembersIdentityAndNoToken() {
        var tokens = mock(FeedTokenRepository.class);
        var identities = mock(MemberIdentityFactory.class);
        var identity = mock(MemberIdentity.class);
        var created = Instant.parse("2026-09-01T00:00:00Z");
        when(tokens.findUseByStation(3)).thenReturn(List.of(new FeedUse(11, created, null, created)));
        when(identities.local(3, 11)).thenReturn(identity);

        var rows = new FeedUseService(tokens, identities).forStation(3);

        assertEquals(List.of(new FeedUseService.FeedUseResponse(11, identity, created, null, created)), rows);
    }
}
