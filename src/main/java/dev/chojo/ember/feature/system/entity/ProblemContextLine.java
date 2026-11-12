/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

import java.time.Instant;

/**
 * One line of the log around an occurrence of a problem.
 *
 * @param loggedAt when it was logged
 * @param level    its severity
 * @param logger   which logger emitted it
 * @param thread   the thread it came from
 * @param message  the line itself, without its stack trace, which the problem already shows
 * @param problem  whether this line is the occurrence itself
 */
public record ProblemContextLine(
        Instant loggedAt, String level, String logger, String thread, String message, boolean problem) {}
