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
 * Document templates for steps as a test decides them: suitable where the predicate says so, and
 * named after their id.
 *
 * @param suitable which templates a step may generate
 */
public record SuitableDocuments(IntPredicate suitable) implements DocumentStepTemplates {
    /** No template is suitable. */
    public static final SuitableDocuments NONE = new SuitableDocuments(template -> false);

    @Override
    public boolean suitable(int stationId, int templateId) {
        return suitable.test(templateId);
    }

    @Override
    public List<StepDocument> named(int stationId, Collection<Integer> templateIds) {
        return templateIds.stream()
                .distinct()
                .map(id -> new StepDocument(id, "Dokument " + id))
                .toList();
    }
}
