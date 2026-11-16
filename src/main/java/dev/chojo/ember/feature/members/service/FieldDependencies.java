/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The rules for a question that is only shown while another question holds an answer that lets it in,
 * the same for a station's questions and an association's.
 *
 * <p>A yes/no question lets it in when answered yes. A choice or a text question lets it in once it is
 * answered at all, which is what a licence class needs before its expiry date makes sense.
 *
 * <p>A question in a group can only be shown behind a question of the same group, so the group reads as
 * one thing wherever it stands. A question on its own may depend on any question of its owner.
 *
 * <p>Only one level deep: a question others depend on cannot depend on one itself. That keeps a form
 * from hiding a question behind a question that is itself hidden, and rules out cycles without having to
 * look for them.
 */
public final class FieldDependencies {

    /**
     * One question of an owner, as far as dependencies care.
     *
     * @param id             the question
     * @param name           what it is called, for naming it in a refusal
     * @param type           what kind of answer it takes
     * @param shownIfFieldId the question it depends on, or {@code null}
     * @param fieldSetId     the group it is in, or {@code null}
     */
    public record Node(
            int id,
            String name,
            FieldType type,
            @Nullable Integer shownIfFieldId,
            @Nullable Integer fieldSetId) {}

    /** The kinds of question another one can be shown behind. */
    private static final Set<FieldType> CONDITIONS =
            Set.of(FieldType.BOOLEAN, FieldType.CHOICE, FieldType.TEXT, FieldType.LONG_TEXT);

    private FieldDependencies() {}

    /** Whether another question can be shown behind a question of this kind. */
    public static boolean canBeCondition(FieldType type) {
        return CONDITIONS.contains(type);
    }

    /**
     * Refuses a dependency that breaks the rules.
     *
     * @param fieldId    the question being made to depend, or 0 for one being created
     * @param controller the question it is to depend on, or {@code null} for none
     * @param owned      every question of the same owner
     * @throws RefusalResponse naming what is wrong
     */
    public static void requireAllowed(int fieldId, @Nullable Integer controller, List<Node> owned) {
        if (controller == null) return;
        if (controller == fieldId) throw MemberRefusal.PROFILE_DEPENDENCY_ON_ITSELF.raise();
        Node target = owned.stream()
                .filter(node -> node.id() == controller)
                .findFirst()
                .orElseThrow(MemberRefusal.PROFILE_DEPENDENCY_NOT_HERE::raise);
        if (!canBeCondition(target.type())) {
            throw MemberRefusal.PROFILE_DEPENDENCY_NOT_A_CONDITION.raise(target.name());
        }
        if (target.shownIfFieldId() != null || !dependents(fieldId, owned).isEmpty()) {
            throw MemberRefusal.PROFILE_DEPENDENCY_CHAIN.raise();
        }
    }

    /**
     * Refuses a question standing in a group while it depends on a question outside that group, or while
     * questions of another group depend on it.
     *
     * @param fieldId    the question, or 0 for one being created
     * @param controller the question it is to depend on, or {@code null} for none
     * @param setAfter   the group it is to be in, or {@code null} for none
     * @param owned      every question of the same owner
     * @throws RefusalResponse naming the question across the line
     */
    public static void requireWithinGroup(
            int fieldId, @Nullable Integer controller, @Nullable Integer setAfter, List<Node> owned) {
        if (setAfter != null && controller != null) {
            owned.stream()
                    .filter(node -> node.id() == controller && !setAfter.equals(node.fieldSetId()))
                    .findFirst()
                    .ifPresent(outside -> {
                        throw MemberRefusal.PROFILE_DEPENDENCY_OUTSIDE_GROUP.raise(outside.name());
                    });
        }
        dependents(fieldId, owned).stream()
                .filter(dependent ->
                        dependent.fieldSetId() != null && !Objects.equals(dependent.fieldSetId(), setAfter))
                .findFirst()
                .ifPresent(left -> {
                    throw MemberRefusal.PROFILE_DEPENDENCY_OUTSIDE_GROUP.raise(left.name());
                });
    }

    /**
     * Refuses deleting a question others depend on, or giving it a type no question can be shown behind.
     *
     * @param fieldId the question
     * @param newType the type it is to have, or {@code null} when it is being deleted
     * @param owned   every question of the same owner
     * @throws RefusalResponse naming the questions that depend on it
     */
    public static void requireNotStillUsed(int fieldId, @Nullable FieldType newType, List<Node> owned) {
        if (newType != null && canBeCondition(newType)) return;
        List<Node> dependents = dependents(fieldId, owned);
        if (dependents.isEmpty()) return;
        throw MemberRefusal.PROFILE_DEPENDENCY_STILL_USED.raise(
                dependents.stream().map(Node::name).collect(Collectors.joining(", ")));
    }

    /**
     * The questions shown only under this one.
     *
     * @param fieldId the question
     * @param owned   every question of the same owner
     * @return the questions that depend on it, empty for none
     */
    public static List<Node> dependentsOf(int fieldId, List<Node> owned) {
        return dependents(fieldId, owned);
    }

    private static List<Node> dependents(int fieldId, List<Node> owned) {
        if (fieldId == 0) return List.of();
        return owned.stream()
                .filter(node -> Objects.equals(node.shownIfFieldId(), fieldId))
                .toList();
    }
}
