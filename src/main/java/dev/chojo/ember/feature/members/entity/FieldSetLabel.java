/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.wrapper.Row;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;

/**
 * The bundle a question belongs to, as a form needs it: which bundle, what it is called, and whether its
 * name stands above its questions as a heading.
 *
 * @param id      the bundle, numbered within its owner
 * @param name    what it is called
 * @param heading whether the forms show the name as a heading above its questions
 */
public record FieldSetLabel(int id, String name, boolean heading) {

    /**
     * The columns a query reads this from, for a field aliased {@code alias} in the given set table.
     *
     * @param alias    the alias of the field row
     * @param setTable the table the owner keeps its bundles in
     * @return the select list, naming {@code field_set_name} and {@code field_set_heading}
     */
    public static String columns(String alias, String setTable) {
        return """
                (SELECT fs.name FROM %2$s fs WHERE fs.id = %1$s.field_set_id) AS field_set_name,
                (SELECT fs.name_as_section FROM %2$s fs WHERE fs.id = %1$s.field_set_id) AS field_set_heading""".formatted(alias, setTable);
    }

    /**
     * Reads the bundle of the field on this row, or nothing where it stands on its own.
     *
     * @param row a row carrying {@code field_set_id} and the columns of {@link #columns}
     * @return the bundle, or {@code null}
     */
    public static @Nullable FieldSetLabel read(Row row) throws SQLException {
        Integer id = row.getObject("field_set_id", Integer.class);
        if (id == null) return null;
        return new FieldSetLabel(id, row.getString("field_set_name"), row.getBoolean("field_set_heading"));
    }
}
