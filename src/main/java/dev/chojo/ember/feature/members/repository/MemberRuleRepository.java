/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.RuleChange;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleNodeKind;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.RuleOwners;
import dev.chojo.ember.feature.members.entity.RuleSubject;
import dev.chojo.ember.feature.members.entity.RuleValue;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** The rules dynamic groups and tags take their members from, and who those rules match. */
@Singleton
public class MemberRuleRepository {

    /**
     * The current members of a station a rule matches, in id order.
     *
     * @param stationId  the station whose members are asked about
     * @param root       the root block of the rule
     * @param today      the current day, which a check counts from unless it names a fixed one
     * @param boundTypes the user types the group is bound to, empty for a tag or an unbound group
     */
    public List<Integer> matching(int stationId, RuleNode root, LocalDate today, List<StationUserType> boundTypes) {
        var compiled = MemberRuleSql.compile(root, today);
        List<String> types = boundTypes.stream().map(Enum::name).toList();
        return query("""
                SELECT sm.id
                FROM station_member sm
                WHERE sm.station_id = :station_id
                  AND NOT sm.former
                  AND (cardinality(:bound_types) = 0 OR sm.user_type = ANY(:bound_types))
                  AND %s
                ORDER BY sm.id;""", compiled.predicate())
                .single(compiled.binds()
                        .apply(call().bind("station_id", stationId).bind("bound_types", types, PostgreSqlTypes.TEXT)))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * The rule of a group or tag, if it is kept by one.
     *
     * @param owner the group or tag
     */
    public Optional<RuleNode> find(RuleOwner owner) {
        String column = ownerColumn(owner);
        var rows = query("""
                WITH RECURSIVE tree AS (SELECT n.* FROM member_rule_node n WHERE n.%s = :owner_id
                                        UNION ALL
                                        SELECT c.* FROM member_rule_node c JOIN tree t ON c.parent_id = t.id)
                SELECT %s FROM tree;""", column, NODE_COLUMNS)
                .single(call().bind("owner_id", owner.id()))
                .map(NodeRow.map())
                .all();
        return Optional.ofNullable(trees(rows).get(owner));
    }

    /**
     * Every rule of a station, keyed by the group or tag it fills.
     *
     * @param stationId the station
     */
    public Map<RuleOwner, RuleNode> findByStation(int stationId) {
        var rows = query("SELECT %s FROM member_rule_node WHERE station_id = :station_id;", NODE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(NodeRow.map())
                .all();
        return trees(rows);
    }

    /** The stations that keep at least one group or tag by a rule. */
    public List<Integer> stationsWithRules() {
        return query("SELECT DISTINCT station_id FROM member_rule_node ORDER BY station_id;")
                .single()
                .map(row -> row.getInt("station_id"))
                .all();
    }

    /**
     * Replaces the rule of a group or tag with the given tree, in one transaction.
     *
     * @param stationId the station the group or tag belongs to
     * @param owner     the group or tag
     * @param root      the root block of the new rule
     */
    public void replace(int stationId, RuleOwner owner, RuleNode root) {
        Transactions.run(() -> {
            delete(owner);
            insert(stationId, owner, null, 0, root);
        });
    }

    /**
     * Removes the rule of a group or tag, which then keeps the members it has by hand.
     *
     * @param owner the group or tag
     * @return whether it had a rule
     */
    public boolean delete(RuleOwner owner) {
        return query("DELETE FROM member_rule_node WHERE %s = :owner_id;", ownerColumn(owner))
                .single(call().bind("owner_id", owner.id()))
                .delete()
                .changed();
    }

    /**
     * Makes the members of a group or tag exactly the given ones, and returns who came in and who went out.
     *
     * @param owner     the group or tag
     * @param memberIds the members it is to hold
     */
    public RuleChange apply(RuleOwner owner, List<Integer> memberIds) {
        String table = owner.kind() == RuleOwner.Kind.GROUP ? "member_group_entry" : "user_tag_entry";
        String column = owner.kind() == RuleOwner.Kind.GROUP ? "group_id" : "tag_id";
        var joined = new ArrayList<Integer>();
        var left = new ArrayList<Integer>();
        query("""
                WITH gone AS (DELETE FROM %1$s e
                              WHERE e.%2$s = :owner_id AND e.member_id <> ALL(:member_ids)
                              RETURNING e.member_id),
                     came AS (INSERT INTO %1$s(%2$s, member_id)
                              SELECT :owner_id, wanted.member_id FROM unnest(:member_ids) AS wanted(member_id)
                              ON CONFLICT DO NOTHING
                              RETURNING member_id)
                SELECT TRUE AS joined, member_id FROM came
                UNION ALL
                SELECT FALSE AS joined, member_id FROM gone;""", table, column)
                .single(call().bind("owner_id", owner.id())
                        .bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(row -> {
                    (row.getBoolean("joined") ? joined : left).add(row.getInt("member_id"));
                    return true;
                })
                .all();
        joined.sort(Comparator.naturalOrder());
        left.sort(Comparator.naturalOrder());
        return new RuleChange(owner, List.copyOf(joined), List.copyOf(left));
    }

    /**
     * The groups and tags of a station that take their members from a rule.
     *
     * @param stationId the station
     */
    public RuleOwners owners(int stationId) {
        var groups = new ArrayList<Integer>();
        var tags = new ArrayList<Integer>();
        query("""
                SELECT group_id, tag_id FROM member_rule_node
                WHERE station_id = :station_id AND parent_id IS NULL
                ORDER BY group_id, tag_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> {
                    var group = row.getObject("group_id", Integer.class);
                    var tag = row.getObject("tag_id", Integer.class);
                    if (group != null) groups.add(group);
                    if (tag != null) tags.add(tag);
                    return true;
                })
                .all();
        return new RuleOwners(List.copyOf(groups), List.copyOf(tags));
    }

    /**
     * Whether a group or tag takes its members from a rule.
     *
     * @param owner the group or tag
     */
    public boolean hasRule(RuleOwner owner) {
        return query(
                        "SELECT EXISTS (SELECT 1 FROM member_rule_node WHERE %s = :owner_id) AS ruled;",
                        ownerColumn(owner))
                .single(call().bind("owner_id", owner.id()))
                .map(row -> row.getBoolean("ruled"))
                .first()
                .orElse(false);
    }

    /**
     * Which of the given groups or tags take their members from a rule.
     *
     * @param kind whether the ids name groups or tags
     * @param ids  the groups or tags asked about
     */
    public Set<Integer> ruled(RuleOwner.Kind kind, Collection<Integer> ids) {
        if (ids.isEmpty()) return Set.of();
        String column = kind == RuleOwner.Kind.GROUP ? "group_id" : "tag_id";
        return new HashSet<>(query("SELECT %1$s AS owner_id FROM member_rule_node WHERE %1$s = ANY(:ids);", column)
                .single(call().bind("ids", List.copyOf(ids), PostgreSqlTypes.INTEGER))
                .map(row -> row.getInt("owner_id"))
                .all());
    }

    /**
     * The names of the groups and tags whose rules ask about a station field, a group or a tag, leaving out the
     * rule of that group or tag itself.
     *
     * @param column    which reference is meant: {@code field_id}, {@code ref_group_id} or {@code ref_tag_id}
     * @param ownColumn the condition on the root that leaves the thing's own rule out
     * @param id        the field, group or tag
     */
    private List<String> askedAboutBy(String column, String ownColumn, int id) {
        return query("""
                WITH RECURSIVE up AS (SELECT n.id, n.parent_id, n.group_id, n.tag_id
                                      FROM member_rule_node n WHERE n.%s = :id
                                      UNION
                                      SELECT p.id, p.parent_id, p.group_id, p.tag_id
                                      FROM member_rule_node p JOIN up ON p.id = up.parent_id)
                SELECT DISTINCT coalesce(g.name, t.name) AS name
                FROM up
                         LEFT JOIN member_group g ON g.id = up.group_id
                         LEFT JOIN user_tag t ON t.id = up.tag_id
                WHERE up.parent_id IS NULL
                  AND %s
                ORDER BY name;""", column, ownColumn)
                .single(call().bind("id", id))
                .map(row -> row.getString("name"))
                .all();
    }

    /** The names of the groups and tags whose rules read a station field. */
    public List<String> askingAboutField(int fieldId) {
        return askedAboutBy("field_id", "TRUE", fieldId);
    }

    /** The names of the groups and tags whose rules ask about a group. */
    public List<String> askingAboutGroup(int groupId) {
        return askedAboutBy("ref_group_id", "up.group_id IS DISTINCT FROM :id", groupId);
    }

    /** The names of the groups and tags whose rules ask about a tag. */
    public List<String> askingAboutTag(int tagId) {
        return askedAboutBy("ref_tag_id", "up.tag_id IS DISTINCT FROM :id", tagId);
    }

    /**
     * Hands the rule of a group or tag over to another one, as when a group becomes a tag.
     *
     * @param from the group or tag the rule fills now
     * @param to   the group or tag it is to fill
     */
    public void moveRule(RuleOwner from, RuleOwner to) {
        query("""
                UPDATE member_rule_node
                SET group_id = :group_id, tag_id = :tag_id
                WHERE %s = :from_id;""", ownerColumn(from))
                .single(call().bind("group_id", to.kind() == RuleOwner.Kind.GROUP ? to.id() : null)
                        .bind("tag_id", to.kind() == RuleOwner.Kind.TAG ? to.id() : null)
                        .bind("from_id", from.id()))
                .update();
    }

    private void insert(int stationId, RuleOwner owner, @Nullable Integer parentId, int position, RuleNode node) {
        boolean root = parentId == null;
        var subject = node.subject();
        var operator = node.operator();
        var value = node.value();
        int id = query("""
                INSERT INTO member_rule_node(station_id, group_id, tag_id, parent_id, position, kind, subject, operator,
                                             field_id, cluster_field_id, ref_group_id, ref_tag_id, value,
                                             reference_date)
                VALUES (:station_id, :group_id, :tag_id, :parent_id, :position, :kind, :subject, :operator, :field_id,
                        :cluster_field_id, :ref_group_id, :ref_tag_id, CAST(:value AS JSONB), :reference_date)
                RETURNING id;""")
                .single(call().bind("station_id", stationId)
                        .bind("group_id", root && owner.kind() == RuleOwner.Kind.GROUP ? owner.id() : null)
                        .bind("tag_id", root && owner.kind() == RuleOwner.Kind.TAG ? owner.id() : null)
                        .bind("parent_id", parentId)
                        .bind("position", position)
                        .bind("kind", node.kind().name())
                        .bind("subject", subject == null ? null : subject.name())
                        .bind("operator", operator == null ? null : operator.name())
                        .bind("field_id", node.fieldId())
                        .bind("cluster_field_id", node.clusterFieldId())
                        .bind("ref_group_id", node.groupId())
                        .bind("ref_tag_id", node.tagId())
                        .bind("value", value == null ? null : value.toJson())
                        .bind("reference_date", node.referenceDate()))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        var children = node.children();
        for (int i = 0; i < children.size(); i++) insert(stationId, owner, id, i, children.get(i));
    }

    private static String ownerColumn(RuleOwner owner) {
        return owner.kind() == RuleOwner.Kind.GROUP ? "group_id" : "tag_id";
    }

    private static Map<RuleOwner, RuleNode> trees(List<NodeRow> rows) {
        var byParent = new HashMap<Integer, List<NodeRow>>();
        var roots = new ArrayList<NodeRow>();
        for (var row : rows) {
            var parent = row.parentId();
            if (parent == null) roots.add(row);
            else byParent.computeIfAbsent(parent, key -> new ArrayList<>()).add(row);
        }
        var trees = new LinkedHashMap<RuleOwner, RuleNode>();
        for (var root : roots) {
            var group = root.groupId();
            var tag = root.tagId();
            var owner = group != null ? RuleOwner.group(group) : RuleOwner.tag(Objects.requireNonNull(tag));
            trees.put(owner, tree(root, byParent));
        }
        return trees;
    }

    private static RuleNode tree(NodeRow row, Map<Integer, List<NodeRow>> byParent) {
        if (row.kind() == RuleNodeKind.CHECK) {
            return RuleNode.check(
                    Objects.requireNonNull(row.subject()),
                    Objects.requireNonNull(row.operator()),
                    row.fieldId(),
                    row.clusterFieldId(),
                    row.refGroupId(),
                    row.refTagId(),
                    row.value(),
                    row.referenceDate());
        }
        var children = byParent.getOrDefault(row.id(), List.of()).stream()
                .sorted(Comparator.comparingInt(NodeRow::position).thenComparingInt(NodeRow::id))
                .map(child -> tree(child, byParent))
                .toList();
        return RuleNode.block(row.kind(), children);
    }

    private static final String NODE_COLUMNS = """
            id, group_id, tag_id, parent_id, position, kind, subject, operator, field_id, cluster_field_id,
            ref_group_id, ref_tag_id, value, reference_date""";

    private record NodeRow(
            int id,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            @Nullable Integer parentId,
            int position,
            RuleNodeKind kind,
            @Nullable RuleSubject subject,
            @Nullable RuleOperator operator,
            @Nullable Integer fieldId,
            @Nullable Integer clusterFieldId,
            @Nullable Integer refGroupId,
            @Nullable Integer refTagId,
            @Nullable RuleValue value,
            @Nullable LocalDate referenceDate) {
        static RowMapping<NodeRow> map() {
            return row -> {
                String subject = row.getString("subject");
                String operator = row.getString("operator");
                String value = row.getString("value");
                return new NodeRow(
                        row.getInt("id"),
                        row.getObject("group_id", Integer.class),
                        row.getObject("tag_id", Integer.class),
                        row.getObject("parent_id", Integer.class),
                        row.getInt("position"),
                        row.getEnum("kind", RuleNodeKind.class),
                        subject == null ? null : RuleSubject.valueOf(subject),
                        operator == null ? null : RuleOperator.valueOf(operator),
                        row.getObject("field_id", Integer.class),
                        row.getObject("cluster_field_id", Integer.class),
                        row.getObject("ref_group_id", Integer.class),
                        row.getObject("ref_tag_id", Integer.class),
                        value == null ? null : RuleValue.parse(value),
                        row.getObject("reference_date", LocalDate.class));
            };
        }
    }
}
