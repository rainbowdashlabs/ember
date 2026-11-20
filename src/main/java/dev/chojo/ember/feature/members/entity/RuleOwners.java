/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.List;

/**
 * The groups and tags of a station that take their members from a rule.
 *
 * @param groupIds the groups
 * @param tagIds   the tags
 */
public record RuleOwners(List<Integer> groupIds, List<Integer> tagIds) {}
