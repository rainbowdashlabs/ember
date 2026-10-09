/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A change of a shared appointment's documents a partner station still has to be told.
 *
 * @param id                 the row
 * @param eventId            the appointment
 * @param partnerId          the partnership with the station to tell
 * @param removedTemplateIds the documents taken off since the partner was last told
 * @param changedAt          when the documents last changed
 * @param deliveryAttempts   how many times in a row telling the partner failed
 */
public record PartnerRequirementChange(
        int id, int eventId, int partnerId, List<Integer> removedTemplateIds, Instant changedAt, int deliveryAttempts) {

    public PartnerRequirementChange {
        removedTemplateIds = List.copyOf(removedTemplateIds);
    }

    /** The columns {@link #map()} reads from {@code partner_requirement_change}. */
    public static final String COLUMNS =
            "id, event_id, partner_id, removed_template_ids, changed_at, delivery_attempts";

    public static RowMapping<PartnerRequirementChange> map() {
        return row -> new PartnerRequirementChange(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getInt("partner_id"),
                List.of((Integer[]) row.getArray("removed_template_ids").getArray()),
                row.get("changed_at", INSTANT_TIMESTAMP),
                row.getInt("delivery_attempts"));
    }
}
