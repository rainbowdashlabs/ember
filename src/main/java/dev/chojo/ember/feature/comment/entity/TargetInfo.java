/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import org.jspecify.annotations.Nullable;

/**
 * What a comment hangs under, as far as the comment system needs to know it: whose it is, what it is
 * called and where its page is.
 *
 * @param type          the kind of target
 * @param id            the appointment, entry, file or ticket
 * @param stationId     the station owning it, {@code null} only for a news entry the instance
 *                      published to every station
 * @param title         what the target is called, which notifications about its comments show
 * @param ticketAddress where a ticket's page is, {@code null} for every other kind
 * @param systemEntry   whether the target is a news entry the instance published to every station
 */
public record TargetInfo(
        CommentEntityType type,
        int id,
        @Nullable Integer stationId,
        String title,
        @Nullable BoardTicketAddress ticketAddress,
        boolean systemEntry) {

    /**
     * A target owned by one station, which is every target but a system news entry.
     *
     * @param type      the kind of target
     * @param id        the target
     * @param stationId the station owning it
     * @param title     what it is called
     * @return the target
     */
    public static TargetInfo of(CommentEntityType type, int id, int stationId, String title) {
        return new TargetInfo(type, id, stationId, title, null, false);
    }
}
