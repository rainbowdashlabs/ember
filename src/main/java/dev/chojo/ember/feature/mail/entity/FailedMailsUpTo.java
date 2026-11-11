/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * How far taking note of a station's failed mails reaches.
 *
 * @param upTo the latest failure the administrator saw, as the station list gave it as its newest;
 *             a failure after it stays unnoticed
 */
public record FailedMailsUpTo(@Nullable Instant upTo) {}
