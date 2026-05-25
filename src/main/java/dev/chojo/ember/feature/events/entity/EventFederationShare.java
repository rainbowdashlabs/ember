/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a federation sharing configuration for an event.
 *
 * @param id      the unique identifier of the share
 * @param eventId the event being shared
 * @param scope   the sharing scope (ALL_PARTNERS or SPECIFIC)
 */
public record EventFederationShare(int id, int eventId, String scope) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<EventFederationShare> map() {
        return row -> new EventFederationShare(row.getInt("id"), row.getInt("event_id"), row.getString("scope"));
    }
}
