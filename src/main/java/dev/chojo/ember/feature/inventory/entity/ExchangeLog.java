/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.time.Instant;

/**
 * One entry of an exchange's history as the exchange pages read it.
 *
 * <p>It carries a step and how it was acknowledged rather than a pair of statuses, because that is
 * what actually happened: somebody whose turn it was said a thing had been done, and the record says
 * whether they were the party it belonged to or standing in for one that cannot answer.
 *
 * @param id         the underlying log entry
 * @param requestId  the exchange the entry belongs to
 * @param stepLabel  the words the step carried when it was walked
 * @param ackKind    whether it was confirmed, asserted or forced
 * @param changedBy  who acknowledged it
 * @param changedAt  when they did
 * @param note       what they wrote alongside
 */
public record ExchangeLog(
        int id, int requestId, String stepLabel, AckKind ackKind, int changedBy, Instant changedAt, String note) {}
