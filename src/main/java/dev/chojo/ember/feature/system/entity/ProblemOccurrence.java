/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

import java.time.Instant;

/**
 * One time a problem happened: when, and on which thread, which is what the log lines around it are
 * found by.
 *
 * @param occurredAt when it happened
 * @param thread     the thread that logged it
 */
public record ProblemOccurrence(Instant occurredAt, String thread) {}
