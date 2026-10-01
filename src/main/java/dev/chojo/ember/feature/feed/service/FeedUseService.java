/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.feed.repository.FeedTokenRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * What a station can see about the subscriptions its members keep.
 *
 * <p>A calendar that a phone fetches every hour and a subscription nobody has ever opened look the
 * same from the inside; the difference is in when it was last fetched. Never with the token: it is
 * the whole key to one person's calendar.
 */
@Singleton
public class FeedUseService {
    private final FeedTokenRepository feedTokens;
    private final MemberIdentityFactory identities;

    @Inject
    public FeedUseService(FeedTokenRepository feedTokens, MemberIdentityFactory identities) {
        this.feedTokens = feedTokens;
        this.identities = identities;
    }

    /**
     * One row per member of the station who has set a subscription up.
     */
    public List<FeedUseResponse> forStation(int stationId) {
        return feedTokens.findUseByStation(stationId).stream()
                .map(use -> new FeedUseResponse(
                        use.memberId(),
                        identities.local(stationId, use.memberId()),
                        use.createdAt(),
                        use.icalPolledAt(),
                        use.notificationPolledAt()))
                .toList();
    }

    /**
     * One member's subscription as the monitoring page reads it.
     *
     * @param memberId             the member
     * @param identity             their name and picture, resolved the way every list resolves them
     * @param createdAt            when the subscription was set up
     * @param icalPolledAt         when a calendar last fetched it, null where none ever has
     * @param notificationPolledAt when a reader last fetched the notifications, null where none has
     */
    public record FeedUseResponse(
            int memberId,
            MemberIdentity identity,
            Instant createdAt,
            @Nullable Instant icalPolledAt,
            @Nullable Instant notificationPolledAt) {}
}
