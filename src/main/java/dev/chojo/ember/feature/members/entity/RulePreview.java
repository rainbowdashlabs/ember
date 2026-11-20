/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.List;

/**
 * Who a rule would hold today, and how that differs from who is in its group or tag now.
 *
 * @param matching every member the rule matches
 * @param joining  the members it would take in
 * @param leaving  the members it would let go
 */
public record RulePreview(List<RuleMember> matching, List<RuleMember> joining, List<RuleMember> leaving) {}
