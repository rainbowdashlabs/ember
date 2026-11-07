/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.repository;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.attendance.entity.TimeRule;
import dev.chojo.ember.feature.attendance.entity.TimeRuleInput;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Reads and writes the preparation and follow-up rules of attendance templates and sheets.
 *
 * <p>Both lists have the same shape and are always written as a whole: a template's rules when the
 * template is saved, a sheet's when it is made from its template or corrected on its own. Each rule
 * keeps whom it names in a restriction table of its own, which {@link RestrictionSql} reads and writes
 * like every other restricted owner's.
 */
@Singleton
public class AttendanceTimeRuleRepository {
    private static final String RULE_COLUMNS =
            "id, position, label, preparation_minutes, follow_up_minutes, restriction_mode, named_kinds";

    /** The two owners of a rule list, with the tables each keeps its rules in. */
    private enum Owner {
        TEMPLATE("attendance_template_time_rule", "template_id", RestrictionType.ATTENDANCE_TEMPLATE_TIME_RULE),
        SESSION("attendance_session_time_rule", "session_id", RestrictionType.ATTENDANCE_SESSION_TIME_RULE);

        private final String table;
        private final String ownerColumn;
        private final RestrictionType restriction;

        Owner(String table, String ownerColumn, RestrictionType restriction) {
            this.table = table;
            this.ownerColumn = ownerColumn;
            this.restriction = restriction;
        }
    }

    /**
     * The rules of a template, in their order.
     *
     * @param templateId the template
     * @return its rules, empty where it has none
     */
    public List<TimeRule> findTemplateRules(int templateId) {
        return findRules(Owner.TEMPLATE, List.of(templateId)).getOrDefault(templateId, List.of());
    }

    /**
     * The rules of a sheet, in their order.
     *
     * @param sessionId the sheet
     * @return its rules, empty where it has none
     */
    public List<TimeRule> findSessionRules(int sessionId) {
        return findRules(Owner.SESSION, List.of(sessionId)).getOrDefault(sessionId, List.of());
    }

    /**
     * The rules of several sheets at once, which is how a report reads them.
     *
     * @param sessionIds the sheets
     * @return each sheet's rules in their order, keyed by sheet; a sheet without rules is missing
     */
    public Map<Integer, List<TimeRule>> findSessionRules(Collection<Integer> sessionIds) {
        return findRules(Owner.SESSION, sessionIds);
    }

    /**
     * Replaces every rule of a template with the given ones, in the order given.
     *
     * @param templateId the template
     * @param rules      the rules it carries from now on
     */
    public void replaceTemplateRules(int templateId, List<TimeRuleInput> rules) {
        replaceRules(Owner.TEMPLATE, templateId, rules);
    }

    /**
     * Replaces every rule of a sheet with the given ones, in the order given.
     *
     * @param sessionId the sheet
     * @param rules     the rules it carries from now on
     */
    public void replaceSessionRules(int sessionId, List<TimeRuleInput> rules) {
        replaceRules(Owner.SESSION, sessionId, rules);
    }

    /**
     * Gives a sheet the rules its template carries right now. From then on the sheet owns them: a later
     * change to the template does not reach it. A rule that lost a group or tag it named is left out,
     * since copying what is left of it would credit people it never named.
     *
     * @param templateId the template the sheet was made from
     * @param sessionId  the sheet
     */
    public void copyTemplateRules(int templateId, int sessionId) {
        List<TimeRuleInput> rules = findTemplateRules(templateId).stream()
                .filter(TimeRule::intact)
                .map(TimeRule::toInput)
                .toList();
        if (rules.isEmpty()) return;
        replaceSessionRules(sessionId, rules);
    }

    private Map<Integer, List<TimeRule>> findRules(Owner owner, Collection<Integer> ownerIds) {
        if (ownerIds.isEmpty()) return Map.of();
        List<OwnedRule> rows = query(
                        """
                SELECT %s AS owner_id, %s FROM %s
                WHERE %s = ANY(:owner_ids)
                ORDER BY %s, position, id;""", owner.ownerColumn, RULE_COLUMNS, owner.table, owner.ownerColumn, owner.ownerColumn)
                .single(call().bind("owner_ids", List.copyOf(ownerIds), PostgreSqlTypes.INTEGER))
                .map(OwnedRule::of)
                .all();
        if (rows.isEmpty()) return Map.of();

        Map<Integer, List<Restriction>> restrictionsByRule = findRestrictions(owner, rows);
        Map<Integer, List<TimeRule>> byOwner = new HashMap<>();
        for (OwnedRule row : rows) {
            List<Restriction> restrictions = restrictionsByRule.getOrDefault(row.id(), List.of());
            var set = new RestrictionSet(restrictions, row.mode());
            var rule = new TimeRule(
                    row.id(),
                    row.position(),
                    row.label(),
                    row.preparationMinutes(),
                    row.followUpMinutes(),
                    RestrictionAudience.of(set),
                    namedKinds(restrictions) >= row.namedKinds());
            byOwner.computeIfAbsent(row.ownerId(), id -> new ArrayList<>()).add(rule);
        }
        return byOwner;
    }

    private static int namedKinds(List<Restriction> restrictions) {
        return (int) restrictions.stream()
                .filter(restriction -> restriction.memberId() == null)
                .count();
    }

    private static int namedKinds(RestrictionSelection selection) {
        return selection.userTypes().size()
                + selection.groupIds().size()
                + selection.tagIds().size();
    }

    private Map<Integer, List<Restriction>> findRestrictions(Owner owner, List<OwnedRule> rules) {
        List<Integer> ruleIds = rules.stream().map(OwnedRule::id).toList();
        Map<Integer, List<Restriction>> byRule = new HashMap<>();
        query("""
                SELECT rule_id, id, user_type, group_id, tag_id, member_id FROM %s
                WHERE rule_id = ANY(:rule_ids)
                ORDER BY id;""", owner.restriction.table())
                .single(call().bind("rule_ids", ruleIds, PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("rule_id"), Restriction.map().map(row)))
                .all()
                .forEach(entry -> byRule.computeIfAbsent(entry.getKey(), id -> new ArrayList<>())
                        .add(entry.getValue()));
        return byRule;
    }

    private void replaceRules(Owner owner, int ownerId, List<TimeRuleInput> rules) {
        Transactions.run(() -> {
            query("DELETE FROM %s WHERE %s = :owner_id;", owner.table, owner.ownerColumn)
                    .single(call().bind("owner_id", ownerId))
                    .delete();
            for (int position = 0; position < rules.size(); position++) {
                insertRule(owner, ownerId, position, rules.get(position));
            }
        });
    }

    private void insertRule(Owner owner, int ownerId, int position, TimeRuleInput rule) {
        var selection = rule.audience().toSelection();
        int ruleId = query("""
                INSERT INTO %s (%s, position, label, preparation_minutes, follow_up_minutes, restriction_mode, named_kinds)
                VALUES (:owner_id, :position, :label, :preparation, :follow_up, :mode, :named_kinds)
                RETURNING id;""", owner.table, owner.ownerColumn)
                .single(call().bind("owner_id", ownerId)
                        .bind("position", position)
                        .bind("label", blankToNull(rule.label()))
                        .bind("preparation", rule.preparationMinutes())
                        .bind("follow_up", rule.followUpMinutes())
                        .bind("mode", selection.mode())
                        .bind("named_kinds", namedKinds(selection)))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        RestrictionSql.replace(owner.restriction.table(), owner.restriction.fkColumn(), ruleId, selection);
    }

    private static @Nullable String blankToNull(@Nullable String label) {
        return label == null || label.isBlank() ? null : label.strip();
    }

    /** One rule row together with the template or sheet it belongs to, before its restrictions are read. */
    private record OwnedRule(
            int ownerId,
            int id,
            int position,
            @Nullable String label,
            int preparationMinutes,
            int followUpMinutes,
            RestrictionMode mode,
            int namedKinds) {
        static OwnedRule of(Row row) throws SQLException {
            return new OwnedRule(
                    row.getInt("owner_id"),
                    row.getInt("id"),
                    row.getInt("position"),
                    row.getString("label"),
                    row.getInt("preparation_minutes"),
                    row.getInt("follow_up_minutes"),
                    row.getEnum("restriction_mode", RestrictionMode.class),
                    row.getInt("named_kinds"));
        }
    }
}
