/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

import java.util.List;

/**
 * @param addedByMemberId the actor who added the members; resolved to a display name in the
 *                        notification handler so the feed entry reads "added by …".
 */
public record MembersAddedToGroup(int stationId, String groupName, List<Integer> memberIds, Integer addedByMemberId)
        implements DomainEvent {}
