/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Data access for the unified restriction tables. Resolving the member identity and the manager
 * bypass happens in {@link dev.chojo.ember.feature.restriction.service.RestrictionService}.
 */
@Singleton
public class RestrictionRepository {

    /**
     * Loads every restriction row of an entity, ordered by id.
     */
    public List<Restriction> findRestrictions(RestrictionType type, int entityId) {
        return RestrictionSql.findRows(type.table(), type.fkColumn(), entityId);
    }

    /**
     * Replaces all restrictions of an entity with the given selection.
     */
    public void setRestrictions(RestrictionType type, int entityId, RestrictionSelection selection) {
        RestrictionSql.replace(type.table(), type.fkColumn(), entityId, selection);
    }

    /**
     * Whether an entity carries any restriction at all.
     */
    public boolean hasRestrictions(RestrictionType type, int entityId) {
        return RestrictionSql.hasAny(type.table(), type.fkColumn(), entityId);
    }

    /**
     * Reads the restriction mode stored on the owning entity, falling back to
     * {@link RestrictionMode#AND} when no such entity exists.
     */
    public RestrictionMode findMode(RestrictionType type, int entityId) {
        return query(
                        "SELECT %s AS mode FROM %s WHERE %s = :id;",
                        type.modeColumn(), type.entityTable(), type.entityIdColumn())
                .single(call().bind("id", entityId))
                .map(row -> RestrictionMode.valueOf(row.getString("mode")))
                .first()
                .orElse(RestrictionMode.AND);
    }

    /**
     * Evaluates the entity's restrictions against a member identity through the
     * {@code check_restriction} database function. Entities without restrictions match everyone.
     */
    public boolean matches(RestrictionType type, int entityId, RestrictionMode mode, RestrictionMember member) {
        return query(
                        "SELECT check_restriction(:rtable, :fk_column, :entity_id, :mode, :member_id, :user_type, :group_ids, :tag_ids) AS result;")
                .single(call().bind("rtable", type.table())
                        .bind("fk_column", type.fkColumn())
                        .bind("entity_id", entityId)
                        .bind("mode", mode.name())
                        .bind("member_id", member.memberId())
                        .bind("user_type", member.userType())
                        .bind("group_ids", member.groupIds(), PostgreSqlTypes.INTEGER)
                        .bind("tag_ids", member.tagIds(), PostgreSqlTypes.INTEGER))
                .map(row -> row.getBoolean("result"))
                .first()
                .orElse(true);
    }
}
