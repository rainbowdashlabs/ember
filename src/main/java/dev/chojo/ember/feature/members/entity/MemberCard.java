/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.MemberIdentity;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a short look at a member's name shows: who they are, who looks after them, whom they look
 * after, and the tags and groups they carry.
 *
 * <p>Everybody at the station reads the same card. Tags, groups and who belongs to whom are known
 * to anybody who spends some time at a station, so none of it is held back by permission.
 *
 * <p>A former member's card names them and nothing more: their relations ended when they left.
 *
 * @param identity the member, with the display details a name is drawn with
 * @param name     the name that says who somebody is and what they are called at once
 * @param former   whether the member has left the station
 * @param parents  the members looking after them
 * @param children the members they look after
 * @param tags     the tags they carry, highest priority first
 * @param groups   the groups they are in, highest priority first
 */
public record MemberCard(
        MemberIdentity identity,
        String name,
        boolean former,
        List<MemberIdentity> parents,
        List<MemberIdentity> children,
        List<MemberCardLabel> tags,
        List<MemberCardLabel> groups) {

    /**
     * A tag or group as the card shows it: its name, in its colour where it has one.
     *
     * @param name  the tag or group name
     * @param color the hex colour, or null for none
     */
    public record MemberCardLabel(String name, @Nullable String color) {}
}
