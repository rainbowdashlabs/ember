/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import dev.chojo.ember.api.MemberIdentity;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A conversation as its header and member list show it.
 *
 * @param conversation the conversation
 * @param members      everybody taking part right now, the longest member first, the reader included
 * @param mine         the reader's own membership
 * @param station      the station a station chat belongs to, or null for every other kind
 * @param guardianFor  for a station chat that takes guardians in, the names of the people each
 *                     guardian in it looks after there, by account; empty otherwise
 * @param moderator    whether the reader handles the chats of a station chat's station, and so may
 *                     remove any message in it
 * @param stationPeople for a station chat, how its station shows each member who is still there,
 *                     with their name, group color and tag, by account; empty otherwise
 */
public record ConversationDetail(
        Conversation conversation,
        List<Membership> members,
        Membership mine,
        @Nullable StationRef station,
        Map<Integer, List<String>> guardianFor,
        boolean moderator,
        Map<Integer, MemberIdentity> stationPeople) {}
