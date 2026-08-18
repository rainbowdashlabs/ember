/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.time.Instant;

/**
 * Per-item snapshot of the most recent check result. Used by the container-walk UI to surface
 * "last checked at X, was Y" hints next to each expected item so the operator can spot items
 * that haven't been verified in a long time.
 *
 * @param itemId      the inventory item the entry refers to
 * @param result      the check result recorded in the most recent check, or {@code null} if the
 *                    item was created after the most recent container check and has never been
 *                    checked yet
 * @param checkedAt   when the most recent check ran, or {@code null} if never checked
 * @param checkerName display name of the member who performed the check, never {@code null} -
 *                    falls back to an empty string when the checker has been removed
 */
public record ItemLastCheck(int itemId, CheckResult result, Instant checkedAt, String checkerName) {}
