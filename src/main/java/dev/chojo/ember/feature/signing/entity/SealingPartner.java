/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A federation partner whose seal a check recognised: its station certificate names this station and was
 * issued by an authority pinned for a partnership with it.
 *
 * @param stationUid the partner station
 * @param name       the name the partnership knows it by; null when the partnership holds none
 */
public record SealingPartner(UUID stationUid, @Nullable String name) {}
