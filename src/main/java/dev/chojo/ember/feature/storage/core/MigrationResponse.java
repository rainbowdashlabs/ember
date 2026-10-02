/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.core;

/**
 * What a move of files carried, for a station, an association's station or the instance.
 *
 * @param totalKeys   every file the move looked at
 * @param copied      the files written to the new storage
 * @param skipped     the files already there, unchanged
 * @param deleted     the files removed from the old storage afterwards
 * @param copiedBytes the size of what was written
 */
public record MigrationResponse(int totalKeys, int copied, int skipped, int deleted, long copiedBytes) {}
