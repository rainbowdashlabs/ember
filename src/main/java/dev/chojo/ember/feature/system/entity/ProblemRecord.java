/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * What the problem log gathered about one problem since it last wrote to the database, which the
 * repository adds to the stored row in one statement.
 *
 * @param fingerprint      what makes two log events the same problem
 * @param level            what kind of problem it is
 * @param logger           which logger reported it
 * @param message          the first message gathered, kept as the row's message when the row is new
 * @param exceptionClass   the class of the exception, or null
 * @param exceptionMessage the message of the latest exception, or null
 * @param stacktrace       the stacktrace of the latest occurrence, or null
 * @param messages         the messages gathered, oldest first; repeats are dropped when stored
 * @param count            how often it happened since the last write
 * @param firstSeen        the first of those occurrences
 * @param lastSeen         the last of those occurrences
 * @param occurrences      the newest of those occurrences, newest first, with their threads
 */
public record ProblemRecord(
        String fingerprint,
        ProblemLevel level,
        String logger,
        String message,
        @Nullable String exceptionClass,
        @Nullable String exceptionMessage,
        @Nullable String stacktrace,
        List<String> messages,
        long count,
        Instant firstSeen,
        Instant lastSeen,
        List<ProblemOccurrence> occurrences) {}
