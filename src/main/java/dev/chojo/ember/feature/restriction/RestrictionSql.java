/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.util.sql.SqlSupport;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Shared SQL for the restriction tables that guard events, quiz tests, forms, news, knowledge base
 * entries, checklists and boards. Every one of those tables has the same shape — a foreign key to
 * the owning entity plus exactly one of {@code user_type}, {@code group_id}, {@code tag_id} or
 * {@code member_id} — so reads, replacements and the visibility predicates only differ in the table
 * and column names.
 *
 * <p>All {@code table}, {@code column} and expression arguments must be trusted compile-time
 * constants: literals declared in a repository or values taken from {@link RestrictionType}. User
 * values always travel as named binds.
 */
public final class RestrictionSql {

    /**
     * The columns of a restriction row, in the order {@link Restriction#map()} reads them.
     */
    private static final String COLUMNS = "id, user_type, group_id, tag_id, member_id";

    private RestrictionSql() {}

    /**
     * Loads every restriction row of an entity, ordered by id.
     *
     * @param table    the restriction table
     * @param fkColumn the column referencing the owning entity
     * @param entityId the owning entity's ID
     */
    public static List<Restriction> findRows(String table, String fkColumn, int entityId) {
        return query("SELECT %s FROM %s WHERE %s = :entity_id ORDER BY id;", COLUMNS, table, fkColumn)
                .single(call().bind("entity_id", entityId))
                .map(Restriction.map())
                .all();
    }

    /**
     * Replaces every restriction row of an entity with the given selection, writing one row per
     * selected value. {@link RestrictionSelection#mode()} is ignored here because the mode lives on
     * the owning entity, and empty lists write nothing — so tables without a {@code member_id}
     * column stay usable as long as no members are selected.
     *
     * @param table     the restriction table
     * @param fkColumn  the column referencing the owning entity
     * @param entityId  the owning entity's ID
     * @param selection the values to persist
     */
    public static void replace(String table, String fkColumn, int entityId, RestrictionSelection selection) {
        query("DELETE FROM %s WHERE %s = :entity_id;", table, fkColumn)
                .single(call().bind("entity_id", entityId))
                .delete();

        for (StationUserType userType : selection.userTypes()) {
            query("INSERT INTO %s (%s, user_type) VALUES (:entity_id, :value);", table, fkColumn)
                    .single(call().bind("entity_id", entityId).bind("value", userType))
                    .insert();
        }
        for (int groupId : selection.groupIds()) {
            insertReference(table, fkColumn, entityId, "group_id", groupId);
        }
        for (int tagId : selection.tagIds()) {
            insertReference(table, fkColumn, entityId, "tag_id", tagId);
        }
        for (int memberId : selection.memberIds()) {
            insertReference(table, fkColumn, entityId, "member_id", memberId);
        }
    }

    /**
     * Reports whether an entity carries any restriction at all.
     *
     * @param table    the restriction table
     * @param fkColumn the column referencing the owning entity
     * @param entityId the owning entity's ID
     */
    public static boolean hasAny(String table, String fkColumn, int entityId) {
        return SqlSupport.exists(
                "SELECT 1 FROM %s WHERE %s = :entity_id LIMIT 1;", call().bind("entity_id", entityId), table, fkColumn);
    }

    /**
     * A {@code restricted} result column reporting whether the selected entity has restrictions.
     *
     * @param type                the restricted entity type
     * @param entityIdExpression  the SQL expression yielding the entity ID, usually {@code n.id}
     */
    public static String restrictedFlag(RestrictionType type, String entityIdExpression) {
        return "%s AS restricted".formatted(existsRestriction(type, entityIdExpression));
    }

    /**
     * A predicate matching only entities without any restriction, used by the public-facing reads
     * that must never expose restricted content.
     *
     * @param type               the restricted entity type
     * @param entityIdExpression the SQL expression yielding the entity ID
     */
    public static String unrestricted(RestrictionType type, String entityIdExpression) {
        return "NOT %s".formatted(existsRestriction(type, entityIdExpression));
    }

    /**
     * A predicate calling the {@code check_restriction} database function, which resolves the
     * member's user type, groups and tags, the entity's restriction mode and the manager bypass on
     * its own.
     *
     * @param type                the restricted entity type
     * @param entityIdExpression  the SQL expression yielding the entity ID, e.g. {@code n.id}
     * @param memberIdExpression  the SQL expression yielding the member ID, e.g. {@code :member_id}
     */
    public static String visibleFor(RestrictionType type, String entityIdExpression, String memberIdExpression) {
        return "check_restriction('%s', '%s', '%s', '%s', %s, %s, '%s')"
                .formatted(
                        type.table(),
                        type.fkColumn(),
                        type.entityTable(),
                        type.entityIdColumn(),
                        entityIdExpression,
                        memberIdExpression,
                        type.managerPermission().name());
    }

    private static String existsRestriction(RestrictionType type, String entityIdExpression) {
        return "EXISTS(SELECT 1 FROM %s r WHERE r.%s = %s)"
                .formatted(type.table(), type.fkColumn(), entityIdExpression);
    }

    private static void insertReference(String table, String fkColumn, int entityId, String valueColumn, int value) {
        query("INSERT INTO %s (%s, %s) VALUES (:entity_id, :value);", table, fkColumn, valueColumn)
                .single(call().bind("entity_id", entityId).bind("value", value))
                .insert();
    }
}
