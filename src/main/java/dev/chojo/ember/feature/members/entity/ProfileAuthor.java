/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import org.jspecify.annotations.Nullable;

/**
 * Who a change to a profile answer is recorded against.
 *
 * <p>Usually a member of the station the answer belongs to: the member, their guardian, or somebody who
 * manages the members there. An association manager often has no membership at that station, or none at
 * all, and is then recorded by their account, so the history can still name them.
 *
 * @param memberId  the author's membership at the member's station, or {@code null} where they have none
 * @param accountId the author's account, or {@code null} where it is not known
 */
public record ProfileAuthor(
        @Nullable Integer memberId, @Nullable Integer accountId) {

    /**
     * Somebody with a membership at the member's station.
     *
     * @param member their membership
     * @return the author
     */
    public static ProfileAuthor member(StationMember member) {
        return new ProfileAuthor(member.id(), member.accountId());
    }

    /**
     * Somebody known by their account only, to be matched to a membership at the member's station where
     * they have one.
     *
     * @param accountId their account
     * @return the author
     */
    public static ProfileAuthor account(int accountId) {
        return new ProfileAuthor(null, accountId);
    }

    /**
     * Nobody anything is known about, which is what a write naming a member that does not exist leaves.
     *
     * @return the author
     */
    public static ProfileAuthor unknown() {
        return new ProfileAuthor(null, null);
    }
}
