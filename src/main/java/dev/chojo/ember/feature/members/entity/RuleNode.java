/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.auth.StationUserType;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * One node of a member rule, with everything below it: a block joining its children, or a check. A
 * check leaves {@code children} empty; a block leaves every check part empty.
 *
 * @param kind          a block kind, or {@link RuleNodeKind#CHECK}
 * @param children      the nodes of a block, in order
 * @param subject       what a check looks at
 * @param operator      how a check compares
 * @param fieldId       the station field a field or age check reads
 * @param clusterFieldId the association field a field or age check reads
 * @param groupId       the group a group check asks about
 * @param tagId         the tag a tag check asks about
 * @param value         what a check compares with
 * @param referenceDate the fixed day a date-relative check counts from, or null for the current day
 */
public record RuleNode(
        RuleNodeKind kind,
        List<RuleNode> children,
        @Nullable RuleSubject subject,
        @Nullable RuleOperator operator,
        @Nullable Integer fieldId,
        @Nullable Integer clusterFieldId,
        @Nullable Integer groupId,
        @Nullable Integer tagId,
        @Nullable RuleValue value,
        @Nullable LocalDate referenceDate) {

    /** A block joining the given nodes. */
    public static RuleNode block(RuleNodeKind kind, List<RuleNode> children) {
        if (!kind.isBlock()) throw new IllegalArgumentException("A check is not a block");
        return new RuleNode(kind, List.copyOf(children), null, null, null, null, null, null, null, null);
    }

    /** A block in which every node holds. */
    public static RuleNode all(RuleNode... children) {
        return block(RuleNodeKind.ALL, List.of(children));
    }

    /** A block in which at least one node holds. */
    public static RuleNode any(RuleNode... children) {
        return block(RuleNodeKind.ANY, List.of(children));
    }

    /** A block in which no node holds. */
    public static RuleNode none(RuleNode... children) {
        return block(RuleNodeKind.NONE, List.of(children));
    }

    /** A check with every part named. */
    public static RuleNode check(
            RuleSubject subject,
            RuleOperator operator,
            @Nullable Integer fieldId,
            @Nullable Integer clusterFieldId,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            @Nullable RuleValue value,
            @Nullable LocalDate referenceDate) {
        return new RuleNode(
                RuleNodeKind.CHECK,
                List.of(),
                subject,
                operator,
                fieldId,
                clusterFieldId,
                groupId,
                tagId,
                value,
                referenceDate);
    }

    /** A check on membership in a group. */
    public static RuleNode group(RuleOperator operator, int groupId) {
        return check(RuleSubject.GROUP, operator, null, null, groupId, null, null, null);
    }

    /** A check on holding a tag. */
    public static RuleNode tag(RuleOperator operator, int tagId) {
        return check(RuleSubject.TAG, operator, null, null, null, tagId, null, null);
    }

    /** A check on the user type. */
    public static RuleNode userType(RuleOperator operator, List<StationUserType> userTypes) {
        return check(RuleSubject.USER_TYPE, operator, null, null, null, null, RuleValue.ofUserTypes(userTypes), null);
    }

    /** A check on how long somebody has been a member on the given day, or today when it is null. */
    public static RuleNode memberSince(
            RuleOperator operator, int amount, RuleUnit unit, @Nullable LocalDate referenceDate) {
        return check(
                RuleSubject.MEMBER_SINCE,
                operator,
                null,
                null,
                null,
                null,
                RuleValue.ofDuration(amount, unit),
                referenceDate);
    }

    /** A check on the age counted from a station date field, on the given day or today when it is null. */
    public static RuleNode age(RuleOperator operator, int fieldId, RuleValue years, @Nullable LocalDate referenceDate) {
        return check(RuleSubject.AGE, operator, fieldId, null, null, null, years, referenceDate);
    }

    /** A check on the age counted from an association date field, on the given day or today when it is null. */
    public static RuleNode clusterAge(
            RuleOperator operator, int clusterFieldId, RuleValue years, @Nullable LocalDate referenceDate) {
        return check(RuleSubject.AGE, operator, null, clusterFieldId, null, null, years, referenceDate);
    }

    /** A check on a station field. */
    public static RuleNode field(int fieldId, RuleOperator operator, RuleValue value) {
        return check(RuleSubject.FIELD, operator, fieldId, null, null, null, value, null);
    }

    /** A check on a station field counting from a fixed day. */
    public static RuleNode field(
            int fieldId, RuleOperator operator, RuleValue value, @Nullable LocalDate referenceDate) {
        return check(RuleSubject.FIELD, operator, fieldId, null, null, null, value, referenceDate);
    }

    /** A check on an association field. */
    public static RuleNode clusterField(int clusterFieldId, RuleOperator operator, RuleValue value) {
        return check(RuleSubject.CLUSTER_FIELD, operator, null, clusterFieldId, null, null, value, null);
    }

    /** The value of a check, or an empty one where it has none. */
    public RuleValue valueOrEmpty() {
        var held = value;
        return held == null ? RuleValue.empty() : held;
    }

    /** This node and every node below it, depth first. */
    public Stream<RuleNode> nodes() {
        return Stream.concat(Stream.of(this), children.stream().flatMap(RuleNode::nodes));
    }

    /** Every check in this node and below it. */
    public List<RuleNode> checks() {
        return nodes().filter(node -> node.kind() == RuleNodeKind.CHECK).toList();
    }

    /** The groups the checks of this tree ask about. */
    public List<Integer> referencedGroups() {
        var ids = new ArrayList<Integer>();
        for (var check : checks()) {
            var id = check.groupId();
            if (check.subject() == RuleSubject.GROUP && id != null) ids.add(id);
        }
        return ids;
    }

    /** The tags the checks of this tree ask about. */
    public List<Integer> referencedTags() {
        var ids = new ArrayList<Integer>();
        for (var check : checks()) {
            var id = check.tagId();
            if (check.subject() == RuleSubject.TAG && id != null) ids.add(id);
        }
        return ids;
    }

    /** How many levels this tree has: one for a single node. */
    public int depth() {
        return 1 + children.stream().mapToInt(RuleNode::depth).max().orElse(0);
    }
}
