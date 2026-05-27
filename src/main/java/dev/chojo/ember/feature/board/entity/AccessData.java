/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import java.util.List;

public record AccessData(List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds) {}
