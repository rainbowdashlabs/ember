/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * A sealed PDF document and how far its seal got.
 *
 * <p>The array is handed over as it is, without a copy, since a sealed document can be large; equality
 * is identity of the array, as for any record holding one.
 *
 * @param pdf           the sealed document
 * @param level         the level the seal reached
 * @param timestampedBy the address of the timestamp service whose timestamp the document carries, or
 *                      null when it carries none
 */
public record SealedDocument(
        byte[] pdf, SealLevel level, @Nullable String timestampedBy) {
    /**
     * A document sealed without a timestamp.
     *
     * @param pdf the sealed document
     * @return the document at {@link SealLevel#BASELINE_B}
     */
    public static SealedDocument withoutTimestamp(byte[] pdf) {
        return new SealedDocument(pdf, SealLevel.BASELINE_B, null);
    }

    /**
     * A document whose seal carries a timestamp.
     *
     * @param pdf           the sealed document
     * @param timestampedBy the address of the timestamp service that answered
     * @return the document at {@link SealLevel#BASELINE_T}
     */
    public static SealedDocument timestamped(byte[] pdf, String timestampedBy) {
        return new SealedDocument(pdf, SealLevel.BASELINE_T, timestampedBy);
    }
}
