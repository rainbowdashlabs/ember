/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record WaitingListEntryGuardian(
        int id, int entryId, String firstname, String lastname, String email, String phone, int position) {

    public static RowMapping<WaitingListEntryGuardian> map() {
        return row -> new WaitingListEntryGuardian(
                row.getInt("id"),
                row.getInt("entry_id"),
                row.getString("firstname"),
                row.getString("lastname"),
                row.getString("email"),
                row.getString("phone"),
                row.getInt("position"));
    }

    public String fullName() {
        return lastname != null && !lastname.isBlank() ? firstname + " " + lastname : firstname;
    }
}
