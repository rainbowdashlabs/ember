/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import dev.chojo.ember.api.auth.StationUserType;

import java.util.List;

public record AccessData(List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {}
