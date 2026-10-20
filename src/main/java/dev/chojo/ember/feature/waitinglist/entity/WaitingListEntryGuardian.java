/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * Somebody who answers for an entry on the list.
 *
 * @param memberId the station member this guardian became when the trial began, {@code null} until
 *                 then
 */
public record WaitingListEntryGuardian(
        int id,
        int entryId,
        String firstname,
        String lastname,
        String email,
        String phone,
        int position,
        @Nullable Integer memberId) {

    public static RowMapping<WaitingListEntryGuardian> map() {
        return row -> new WaitingListEntryGuardian(
                row.getInt("id"),
                row.getInt("entry_id"),
                row.getString("firstname"),
                row.getString("lastname"),
                row.getString("email"),
                row.getString("phone"),
                row.getInt("position"),
                row.getObject("member_id", Integer.class));
    }

    public String fullName() {
        return lastname != null && !lastname.isBlank() ? firstname + " " + lastname : firstname;
    }
}
