/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.List;
import java.util.Map;

/**
 * The values of the placeholders a template names, filled in for one member.
 *
 * @param values   the value of every key that has one, empty for the second guardian of a member who
 *                 has a single guardian or none
 * @param missing  the keys the template names that have no value for this member, in template order
 * @param subjects every person whose data the values hold: the member, and each guardian named
 */
public record ResolvedValues(Map<String, String> values, List<String> missing, List<DataSubject> subjects) {

    /** Whether every key the template names has a value. */
    public boolean complete() {
        return missing.isEmpty();
    }
}
