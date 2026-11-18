/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.List;

/**
 * What the rule builder needs to offer only what can be saved: every operator per subject and field type, and how
 * large a rule may grow.
 *
 * @param operators every operator a check can use
 * @param maxDepth  how many levels of blocks and checks a rule may have
 * @param maxChecks how many checks a rule may hold
 */
public record RuleOperatorTable(List<RuleOperatorOption> operators, int maxDepth, int maxChecks) {}
