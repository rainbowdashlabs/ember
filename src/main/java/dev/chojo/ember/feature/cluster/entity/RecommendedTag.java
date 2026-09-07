/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

/**
 * A word an association recommends, as it reads at one of its stations.
 *
 * <p>{@code adopted} is what keeps the recommendation from looking like a duplicate to clear up: a
 * station that already has a row for the same word is not being asked to do anything, because the
 * two rows are one word already.
 *
 * @param name    the word as the association spelled it
 * @param color   the badge colour the association chose, or {@code null}
 * @param adopted whether the station already has a tag meaning the same word
 */
public record RecommendedTag(String name, String color, boolean adopted) {}
