/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * An appointment that is a part of an event group.
 *
 * @param eventId  the appointment
 * @param setId    the set it belongs to
 * @param eventGroupId the event group, through the set
 */
public record EventGroupPart(int eventId, int setId, int eventGroupId) {

    /** Reads a part from a row holding {@code event_id}, {@code set_id} and {@code group_id}. */
    public static RowMapping<EventGroupPart> map() {
        return row -> new EventGroupPart(row.getInt("event_id"), row.getInt("set_id"), row.getInt("group_id"));
    }
}
