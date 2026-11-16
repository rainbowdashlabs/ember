/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.federation.entity.ShareScope;

import java.util.List;

/**
 * How an event group is shared with partner stations.
 *
 * @param eventGroupId   the event group
 * @param scope      every partner, or the ones named
 * @param partnerIds the partners named where the scope names them
 */
public record EventGroupShare(int eventGroupId, ShareScope scope, List<Integer> partnerIds) {}
