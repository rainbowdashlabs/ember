/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A partner station as a public page names it.
 *
 * @param uid        the partner's public id
 * @param name       the partner's name
 * @param slug       the partner's public address, where it has one
 * @param distanceKm how far away the partner is, where both stations have a location
 */
public record PublicPartnerSummary(UUID uid, String name, @Nullable String slug, @Nullable Double distanceKm) {}
