/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

import java.util.List;

/**
 * The log around one occurrence of a problem.
 *
 * @param state          what was found, and why nothing was where nothing was
 * @param databaseLogging whether the log is written to the database at all
 * @param occurrences    the occurrences kept beside the problem, the newest first, to choose from
 * @param occurrence     which of them the lines are around
 * @param allThreads     whether the lines of every thread are listed, not only the occurrence's own
 * @param lines          the lines, oldest first
 */
public record ProblemContext(
        ProblemContextState state,
        boolean databaseLogging,
        List<ProblemOccurrence> occurrences,
        int occurrence,
        boolean allThreads,
        List<ProblemContextLine> lines) {}
