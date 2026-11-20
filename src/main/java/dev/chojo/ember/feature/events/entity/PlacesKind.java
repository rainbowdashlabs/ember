/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;

/**
 * What a partner arrangement is about, with the table it is kept in and the refusals that name it.
 * Every arrangement has the same shape, {@link PartnerPlaces}; only where it lives and how a refusal
 * about it reads differ.
 */
public enum PlacesKind {
    /** A shared appointment, whose places count per date. */
    APPOINTMENT(
            "event_partner_places",
            "event_id",
            "station_event",
            EventRefusal.PARTNER_DECIDES_ITS_OWN,
            EventRefusal.PARTNER_DOES_NOT_CONFIRM_ITS_OWN);

    private final String table;
    private final String ownerColumn;
    private final String ownerTable;
    private final Refusal decidesItsOwn;
    private final Refusal doesNotConfirm;

    PlacesKind(String table, String ownerColumn, String ownerTable, Refusal decidesItsOwn, Refusal doesNotConfirm) {
        this.table = table;
        this.ownerColumn = ownerColumn;
        this.ownerTable = ownerTable;
        this.decidesItsOwn = decidesItsOwn;
        this.doesNotConfirm = doesNotConfirm;
    }

    /**
     * The table the arrangements of this kind are kept in, a constant fit to be spliced into SQL.
     *
     * @return the table name
     */
    public String table() {
        return table;
    }

    /**
     * The column of that table naming the owner, a constant fit to be spliced into SQL.
     *
     * @return the column name
     */
    public String ownerColumn() {
        return ownerColumn;
    }

    /**
     * The table the owners of this kind are kept in, keyed by {@code id}, a constant fit to be spliced into
     * SQL.
     *
     * @return the table name
     */
    public String ownerTable() {
        return ownerTable;
    }

    /**
     * The refusal for a host deciding about a partner's member where the partner decides its own.
     *
     * @return the refusal
     */
    public Refusal decidesItsOwn() {
        return decidesItsOwn;
    }

    /**
     * The refusal for a partner confirming its own member where the host was not asked to let it.
     *
     * @return the refusal
     */
    public Refusal doesNotConfirm() {
        return doesNotConfirm;
    }
}
