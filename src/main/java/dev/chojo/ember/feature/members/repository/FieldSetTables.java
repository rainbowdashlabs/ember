/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.members.entity.FieldSet;
import dev.chojo.ember.feature.members.entity.FieldSetPlacement;
import dev.chojo.ember.util.sql.SqlSupport;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The bundles of one kind of owner's profile questions, written the same way for a station and an
 * association: each keeps its bundles and its questions in tables of its own.
 */
public abstract class FieldSetTables {

    private final String setTable;
    private final String fieldTable;
    private final String ownerColumn;

    /**
     * @param setTable    the table the owner keeps its bundles in
     * @param fieldTable  the table the owner keeps its questions in
     * @param ownerColumn the column both tables name the owner in
     */
    protected FieldSetTables(String setTable, String fieldTable, String ownerColumn) {
        this.setTable = setTable;
        this.fieldTable = fieldTable;
        this.ownerColumn = ownerColumn;
    }

    private String columns() {
        return "id, %s AS owner_id, name, name_as_section".formatted(ownerColumn);
    }

    /** Every bundle of one owner, by name. */
    public List<FieldSet> findByOwner(int ownerId) {
        return query("SELECT %s FROM %s WHERE %s = :owner ORDER BY name;", columns(), setTable, ownerColumn)
                .single(call().bind("owner", ownerId))
                .map(FieldSet.map())
                .all();
    }

    public Optional<FieldSet> findById(int id) {
        return query("SELECT %s FROM %s WHERE id = :id;", columns(), setTable)
                .single(call().bind("id", id))
                .map(FieldSet.map())
                .first();
    }

    /** Writes a new, empty bundle down. */
    public FieldSet create(int ownerId, String name, boolean nameAsSection) {
        return SqlSupport.insertReturning(
                "INSERT INTO %s(%s, name, name_as_section) VALUES (:owner, :name, :heading) RETURNING %%s;"
                        .formatted(setTable, ownerColumn),
                call().bind("owner", ownerId).bind("name", name).bind("heading", nameAsSection),
                FieldSet.map(),
                columns());
    }

    /** Renames a bundle, or changes whether its name stands above its questions. */
    public boolean update(int id, String name, boolean nameAsSection) {
        return query("UPDATE %s SET name = :name, name_as_section = :heading WHERE id = :id;", setTable)
                .single(call().bind("id", id).bind("name", name).bind("heading", nameAsSection))
                .update()
                .changed();
    }

    /** Dissolves a bundle. Its questions stay, on their own. */
    public boolean delete(int id) {
        return SqlSupport.deleteById(setTable, id);
    }

    /**
     * Puts a question into a bundle, at its end, or takes it out of the one it is in.
     *
     * @param fieldId the question
     * @param setId   the bundle, or {@code null} to stand on its own
     */
    public void place(int fieldId, @Nullable Integer setId) {
        query("""
                UPDATE %1$s
                SET field_set_id = :set_id,
                    set_position = coalesce((SELECT max(other.set_position) + 1
                                             FROM %1$s other
                                             WHERE other.field_set_id = :set_id
                                               AND other.id <> :field_id), 0)
                WHERE id = :field_id;""", fieldTable)
                .single(call().bind("field_id", fieldId).bind("set_id", setId))
                .update();
    }

    /** Puts a bundle's questions in the given order, in one write. */
    public int applyOrder(int setId, List<Integer> fieldIds) {
        if (fieldIds.isEmpty()) return 0;
        return query("""
                UPDATE %s AS f
                SET set_position = ordered.position
                FROM unnest(CAST(:ids AS INTEGER[])) WITH ORDINALITY AS ordered(id, position)
                WHERE f.id = ordered.id
                  AND f.field_set_id = :set_id;""", fieldTable)
                .single(call().bind("ids", fieldIds, PostgreSqlTypes.INTEGER).bind("set_id", setId))
                .update()
                .rows();
    }

    /** The questions of a bundle, in the bundle's order. */
    public List<Integer> members(int setId) {
        return query("SELECT id FROM %s WHERE field_set_id = :set_id ORDER BY set_position, id;", fieldTable)
                .single(call().bind("set_id", setId))
                .map(row -> row.getInt("id"))
                .all();
    }

    /** Where each of an owner's bundled questions stands within its bundle. */
    public Map<Integer, FieldSetPlacement> placements(int ownerId) {
        Map<Integer, FieldSetPlacement> placements = new HashMap<>();
        query(
                        "SELECT id, field_set_id, set_position FROM %s WHERE %s = :owner AND field_set_id IS NOT NULL;",
                        fieldTable, ownerColumn)
                .single(call().bind("owner", ownerId))
                .map(row -> Map.entry(
                        row.getInt("id"),
                        new FieldSetPlacement(row.getInt("field_set_id"), row.getInt("set_position"))))
                .all()
                .forEach(entry -> placements.put(entry.getKey(), entry.getValue()));
        return placements;
    }
}
