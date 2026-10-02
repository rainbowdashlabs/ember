/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.time.Instant;

/**
 * Summary of unacknowledged changes for a single member.
 *
 * @param memberId     the member identifier
 * @param memberName   the member's display name
 * @param pendingCount the number of unacknowledged changes
 * @param latestChange the timestamp of the most recent change
 */
public record MemberChangeSummary(int memberId, String memberName, int pendingCount, Instant latestChange) {}
