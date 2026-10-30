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
import java.util.stream.Collectors;

/**
 * The rules for a question that is only shown while a yes/no question is answered yes, the same for a
 * station's questions and an association's.
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
     */
    public record Node(
            int id, String name, FieldType type, @Nullable Integer shownIfFieldId) {}

    private FieldDependencies() {}

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
        if (target.type() != FieldType.BOOLEAN) {
            throw MemberRefusal.PROFILE_DEPENDENCY_NOT_YES_NO.raise(target.name());
        }
        if (target.shownIfFieldId() != null || !dependents(fieldId, owned).isEmpty()) {
            throw MemberRefusal.PROFILE_DEPENDENCY_CHAIN.raise();
        }
    }

    /**
     * Refuses deleting a yes/no question, or giving it another type, while others depend on it.
     *
     * @param fieldId the question
     * @param newType the type it is to have, or {@code null} when it is being deleted
     * @param owned   every question of the same owner
     * @throws RefusalResponse naming the questions that depend on it
     */
    public static void requireNotStillUsed(int fieldId, @Nullable FieldType newType, List<Node> owned) {
        if (newType == FieldType.BOOLEAN) return;
        List<Node> dependents = dependents(fieldId, owned);
        if (dependents.isEmpty()) return;
        throw MemberRefusal.PROFILE_DEPENDENCY_STILL_USED.raise(
                dependents.stream().map(Node::name).collect(Collectors.joining(", ")));
    }

    private static List<Node> dependents(int fieldId, List<Node> owned) {
        if (fieldId == 0) return List.of();
        return owned.stream()
                .filter(node -> Objects.equals(node.shownIfFieldId(), fieldId))
                .toList();
    }
}
