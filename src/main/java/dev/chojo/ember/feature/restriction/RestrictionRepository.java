/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Repository for unified restriction CRUD operations.
 * Works with any entity's restriction table via the table/column name parameters.
 */
@Singleton
public class RestrictionRepository {

    /**
     * Finds all restrictions for an entity.
     *
     * @param table      the restriction table name (e.g. "event_restriction")
     * @param fkColumn   the foreign key column name (e.g. "event_id")
     * @param entityId   the entity ID
     * @return list of restrictions
     */
    public List<Restriction> findRestrictions(String table, String fkColumn, int entityId) {
        return Query.query("SELECT * FROM " + table + " WHERE " + fkColumn + " = :entity_id ORDER BY id;")
                .single(Call.of().bind("entity_id", entityId))
                .map(Restriction.map())
                .all();
    }

    /**
     * Replaces all restrictions for an entity.
     * Deletes existing restrictions and inserts the new ones.
     *
     * @param table      the restriction table name
     * @param fkColumn   the foreign key column name
     * @param entityId   the entity ID
     * @param roleIds    role IDs to restrict to
     * @param groupIds   group IDs to restrict to
     * @param tagIds     tag IDs to restrict to
     * @param memberIds  member IDs to restrict to
     */
    public void setRestrictions(
            String table,
            String fkColumn,
            int entityId,
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        // Delete existing
        Query.query("DELETE FROM " + table + " WHERE " + fkColumn + " = :entity_id;")
                .single(Call.of().bind("entity_id", entityId))
                .delete();

        // Insert new restrictions
        for (int roleId : roleIds) {
            Query.query("INSERT INTO " + table + "(" + fkColumn + ", role_id) VALUES (:entity_id, :role_id);")
                    .single(Call.of().bind("entity_id", entityId).bind("role_id", roleId))
                    .insert();
        }
        for (int groupId : groupIds) {
            Query.query("INSERT INTO " + table + "(" + fkColumn + ", group_id) VALUES (:entity_id, :group_id);")
                    .single(Call.of().bind("entity_id", entityId).bind("group_id", groupId))
                    .insert();
        }
        for (int tagId : tagIds) {
            Query.query("INSERT INTO " + table + "(" + fkColumn + ", tag_id) VALUES (:entity_id, :tag_id);")
                    .single(Call.of().bind("entity_id", entityId).bind("tag_id", tagId))
                    .insert();
        }
        for (int memberId : memberIds) {
            Query.query("INSERT INTO " + table + "(" + fkColumn + ", member_id) VALUES (:entity_id, :member_id);")
                    .single(Call.of().bind("entity_id", entityId).bind("member_id", memberId))
                    .insert();
        }
    }

    /**
     * Loads restrictions and wraps them in a {@link RestrictionSet} with the given mode.
     *
     * @param table    the restriction table name
     * @param fkColumn the foreign key column name
     * @param entityId the entity ID
     * @param mode     the restriction mode
     * @return a RestrictionSet
     */
    public RestrictionSet findRestrictionSet(String table, String fkColumn, int entityId, RestrictionMode mode) {
        return new RestrictionSet(findRestrictions(table, fkColumn, entityId), mode);
    }

    /**
     * Checks if a member passes the restrictions for an entity using the DB function.
     * The DB function resolves:
     * <ul>
     *   <li>The member's identity (roles with inheritance, groups, tags)</li>
     *   <li>The entity's restriction mode (AND/OR)</li>
     *   <li>Manager bypass (if member has the management role, restrictions are skipped)</li>
     * </ul>
     * The caller only needs entity type, entity ID, and member ID.
     *
     * @param type     the restriction type
     * @param entityId the entity ID
     * @param memberId the member to check
     * @return true if the member passes the restrictions
     */
    public boolean checkRestriction(RestrictionType type, int entityId, int memberId) {
        return Query.query(
                        "SELECT check_restriction(:rtable, :fk_column, :etable, :eid_column, :entity_id, :member_id, :manager_role) AS result;")
                .single(Call.of()
                        .bind("rtable", type.table())
                        .bind("fk_column", type.fkColumn())
                        .bind("etable", type.entityTable())
                        .bind("eid_column", type.entityIdColumn())
                        .bind("entity_id", entityId)
                        .bind("member_id", memberId)
                        .bind("manager_role", type.managerRole()))
                .map(row -> row.getBoolean("result"))
                .first()
                .orElse(true);
    }

    /**
     * Checks whether any restrictions exist for an entity.
     */
    public boolean hasRestrictions(String table, String fkColumn, int entityId) {
        return Query.query("SELECT EXISTS(SELECT 1 FROM " + table + " WHERE " + fkColumn + " = :entity_id) AS has;")
                .single(Call.of().bind("entity_id", entityId))
                .map(row -> row.getBoolean("has"))
                .first()
                .orElse(false);
    }
}
