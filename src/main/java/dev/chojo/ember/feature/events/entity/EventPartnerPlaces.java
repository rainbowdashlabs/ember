/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * What a partner station may do with an appointment shared with it.
 *
 * <p>Three arrangements come out of the pair, and the one nobody has configured is the one the
 * product always had: the host decides, member by member.
 *
 * @param slotBudget      how many places this partner may fill on one date, or {@code null} for no cap
 * @param partnerConfirms whether the partner decides who fills them rather than the host
 */
public record EventPartnerPlaces(int eventId, int partnerId, Integer slotBudget, boolean partnerConfirms) {

    /** What holds where nobody has said otherwise: the host decides and there is no cap. */
    public static EventPartnerPlaces hostDecides(int eventId, int partnerId) {
        return new EventPartnerPlaces(eventId, partnerId, null, false);
    }

    /** Whether this partner may fill another place, given how many it has filled already. */
    public boolean hasRoomBeyond(int alreadyTaken) {
        return slotBudget == null || alreadyTaken < slotBudget;
    }

    public static RowMapping<EventPartnerPlaces> map() {
        return row -> new EventPartnerPlaces(
                row.getInt("event_id"),
                row.getInt("partner_id"),
                row.getObject("slot_budget", Integer.class),
                row.getBoolean("partner_confirms"));
    }
}
