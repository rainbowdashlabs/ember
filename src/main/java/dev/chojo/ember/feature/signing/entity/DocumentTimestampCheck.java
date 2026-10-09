/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * A timestamp on the document itself, in a revision of its own, as added when a seal is lifted to a
 * timestamped level later.
 *
 * @param timestamp       the timestamp and its result
 * @param coversWholeFile whether the revision it stamps runs to the end of the file, so nothing was
 *                        appended after it
 */
public record DocumentTimestampCheck(TimestampCheck timestamp, boolean coversWholeFile) {}
