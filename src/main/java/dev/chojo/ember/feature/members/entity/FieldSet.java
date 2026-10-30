/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A bundle of an owner's profile questions that belong together, such as an address.
 *
 * <p>Its questions are put to an audience together and stand together on every form. Dissolving it leaves
 * the questions as they are. A station and an association each keep their own, numbered apart.
 *
 * @param id            the bundle
 * @param ownerId       the station or association it belongs to
 * @param name          what it is called
 * @param nameAsSection whether the forms show the name as a heading above its questions
 */
public record FieldSet(int id, int ownerId, String name, boolean nameAsSection) {

    /**
     * Creates a row mapping for a row whose owner column is read as {@code owner_id}.
     */
    public static RowMapping<FieldSet> map() {
        return row -> new FieldSet(
                row.getInt("id"), row.getInt("owner_id"), row.getString("name"), row.getBoolean("name_as_section"));
    }
}
