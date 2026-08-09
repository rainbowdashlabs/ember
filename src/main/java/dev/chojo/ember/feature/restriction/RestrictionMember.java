/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import dev.chojo.ember.api.auth.StationUserType;

import java.util.List;

/**
 * The identity a restriction is evaluated against: the member itself plus the user type, groups and
 * tags that a restriction row can name. Bundles the four values that the restriction check needs so
 * they travel together instead of as a parameter list.
 *
 * @param memberId the member being checked
 * @param userType the member's user type
 * @param groupIds the IDs of every group the member belongs to
 * @param tagIds   the IDs of every tag assigned to the member
 */
public record RestrictionMember(int memberId, StationUserType userType, List<Integer> groupIds, List<Integer> tagIds) {}
