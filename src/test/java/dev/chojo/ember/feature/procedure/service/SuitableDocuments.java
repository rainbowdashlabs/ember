/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import java.util.Collection;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Document templates for steps as a test decides them: suitable where the predicate says so, meant for the
 * members the other predicate takes in, and named after their id.
 *
 * @param suitable which templates a step may generate
 * @param meantFor which members every template is meant for
 */
public record SuitableDocuments(IntPredicate suitable, IntPredicate meantFor) implements DocumentStepTemplates {
    /** No template is suitable. */
    public static final SuitableDocuments NONE = new SuitableDocuments(template -> false);

    /**
     * Templates suitable where the predicate says so and meant for every member.
     *
     * @param suitable which templates a step may generate
     */
    public SuitableDocuments(IntPredicate suitable) {
        this(suitable, member -> true);
    }

    @Override
    public boolean suitable(int stationId, int templateId) {
        return suitable.test(templateId);
    }

    @Override
    public boolean meantFor(int stationId, int templateId, int memberId) {
        return meantFor.test(memberId);
    }

    @Override
    public List<StepDocument> named(int stationId, Collection<Integer> templateIds) {
        return templateIds.stream()
                .distinct()
                .map(id -> new StepDocument(id, "Dokument " + id))
                .toList();
    }
}
