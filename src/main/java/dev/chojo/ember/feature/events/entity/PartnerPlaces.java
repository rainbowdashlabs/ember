/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.refusal.EventRefusal;
import org.jspecify.annotations.Nullable;

/**
 * What a partner station may do with something shared with it that takes sign-ups: how many places it
 * fills, and whether it decides who fills them.
 *
 * <p>Three arrangements come out of the pair, and the one nobody has configured is the one the
 * product always had: the host decides, member by member. A budget always means the partner decides.
 *
 * @param ownerId         the appointment or event group the arrangement is about
 * @param partnerId       the host's own record of the partner
 * @param slotBudget      how many places this partner may fill, or {@code null} for no cap
 * @param partnerConfirms whether the partner decides who fills them rather than the host
 */
public record PartnerPlaces(
        int ownerId, int partnerId, @Nullable Integer slotBudget, boolean partnerConfirms) {

    /**
     * What holds where nobody has said otherwise: the host decides and there is no cap.
     *
     * @param ownerId   the appointment or event group
     * @param partnerId the host's own record of the partner
     * @return the arrangement nobody configured
     */
    public static PartnerPlaces hostDecides(int ownerId, int partnerId) {
        return new PartnerPlaces(ownerId, partnerId, null, false);
    }

    /**
     * The arrangement asked for, put into the one shape it is stored in. A budget means the partner
     * decides, so asking for a budget without the decision gives the decision as well, and taking the
     * decision back takes the budget with it.
     *
     * @param ownerId         the appointment or event group
     * @param partnerId       the host's own record of the partner
     * @param slotBudget      how many places were asked for, or {@code null} for no cap
     * @param partnerConfirms whether the partner was asked to decide
     * @return the arrangement as it is kept
     */
    public static PartnerPlaces requested(
            int ownerId, int partnerId, @Nullable Integer slotBudget, boolean partnerConfirms) {
        if (slotBudget != null && slotBudget < 0) {
            throw EventRefusal.PLACES_CANNOT_BE_NEGATIVE.raise();
        }
        boolean decides = partnerConfirms || slotBudget != null;
        return new PartnerPlaces(ownerId, partnerId, decides ? slotBudget : null, decides);
    }

    /**
     * Whether the host decides, as it does where nothing was arranged.
     *
     * @return true where the partner was not handed the decision
     */
    public boolean decidedByHost() {
        return !partnerConfirms;
    }

    /**
     * Whether one more of the partner's members fits into its places.
     *
     * @param taken   how many of its places are filled
     * @param waiting how many of its members wait on the waiting list, each of whom a place is kept for
     * @return true where there is no cap or the cap is not reached
     */
    public boolean admits(int taken, int waiting) {
        Integer budget = slotBudget;
        return budget == null || taken + waiting < budget;
    }

    /**
     * Reads an arrangement from a row naming its owner {@code owner_id}.
     *
     * @return the mapping
     */
    public static RowMapping<PartnerPlaces> map() {
        return row -> new PartnerPlaces(
                row.getInt("owner_id"),
                row.getInt("partner_id"),
                row.getObject("slot_budget", Integer.class),
                row.getBoolean("partner_confirms"));
    }
}
