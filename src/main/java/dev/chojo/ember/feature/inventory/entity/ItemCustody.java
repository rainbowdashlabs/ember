/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Names the party that has an item right now, which is a different question from
 * {@link ItemOwner}: a station can hold gear it does not own, and an owner can be holding gear
 * nobody at the station has seen for a year.
 *
 * <p>Custody is stored rather than derived. Each value carries exactly one set of pointers, and the
 * database enforces that with a check per value.
 */
public enum ItemCustody {
    /**
     * In the store of whoever owns it. No station is named, because for a station-owned item the
     * owner is the station itself.
     */
    WITH_OWNER,
    /**
     * A station that is not the owner holds it. The custody station names that station.
     */
    AT_STATION,
    /**
     * A member holds it. The assignment names them and the custody station names the station they
     * hold it through.
     */
    WITH_MEMBER,
    /**
     * Lent to a federation partner through the existing lending flow. The custody station names the
     * lender.
     */
    WITH_PARTNER,
    /**
     * Between two parties. The custody movement names the movement holding it.
     */
    IN_TRANSIT,
    /**
     * Missing. The lost timestamp says when it went, and whoever had it keeps it on their record
     * until it is replaced, which is what makes it visible that they are short of it.
     */
    LOST;

    /**
     * Whether an item in this custody may be handed to a member. Gear on its way somewhere, gear a
     * partner has and gear nobody can find are all unavailable, however free they look.
     *
     * @return {@code true} when the item is available to hand out
     */
    public boolean assignable() {
        return this == WITH_OWNER || this == AT_STATION || this == WITH_MEMBER;
    }
}
