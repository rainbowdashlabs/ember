/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.core;

import org.jspecify.annotations.Nullable;

/**
 * Whether a storage answered a connection test.
 *
 * @param healthy   whether it answered
 * @param error     what went wrong, as the owner may be told it; {@code null} when it answered
 * @param checkedAt when it was asked
 */
public record ProbeResult(boolean healthy, @Nullable String error, String checkedAt) {}
