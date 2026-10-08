/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A sealed PDF document and how far its seal got.
 *
 * <p>The array is handed over as it is, without a copy, since a sealed document can be large; equality
 * is identity of the array, as for any record holding one.
 *
 * @param pdf                 the sealed document
 * @param level               the level the seal reached
 * @param timestampedBy       the address of the timestamp service whose timestamp the document carries, the
 *                            newest where it carries several, or null when it carries none
 * @param timestampValidUntil the earliest end of validity among the certificates the newest timestamp rests
 *                            on, before which a later timestamp has to cover it, or null without a timestamp
 */
public record SealedDocument(
        byte[] pdf,
        SealLevel level,
        @Nullable String timestampedBy,
        @Nullable Instant timestampValidUntil) {
    /**
     * A document sealed without a timestamp.
     *
     * @param pdf the sealed document
     * @return the document at {@link SealLevel#BASELINE_B}
     */
    public static SealedDocument withoutTimestamp(byte[] pdf) {
        return new SealedDocument(pdf, SealLevel.BASELINE_B, null, null);
    }

    /**
     * A document whose seal carries a timestamp.
     *
     * @param pdf           the sealed document
     * @param timestampedBy the address of the timestamp service that answered
     * @param validUntil    when the certificates the timestamp rests on start running out
     * @return the document at {@link SealLevel#BASELINE_T}
     */
    public static SealedDocument timestamped(byte[] pdf, String timestampedBy, Instant validUntil) {
        return new SealedDocument(pdf, SealLevel.BASELINE_T, timestampedBy, validUntil);
    }

    /**
     * A document whose seal carries a timestamp and the validation material for both.
     *
     * @param pdf           the sealed document
     * @param timestampedBy the address of the timestamp service that answered
     * @param validUntil    when the certificates the timestamp rests on start running out
     * @return the document at {@link SealLevel#BASELINE_LT}
     */
    public static SealedDocument longTerm(byte[] pdf, String timestampedBy, Instant validUntil) {
        return new SealedDocument(pdf, SealLevel.BASELINE_LT, timestampedBy, validUntil);
    }

    /**
     * The same document at {@link SealLevel#BASELINE_LTA}, for one whose validation material a later document
     * timestamp covers.
     *
     * @return the document at {@link SealLevel#BASELINE_LTA}
     */
    public SealedDocument archived() {
        return new SealedDocument(pdf, SealLevel.BASELINE_LTA, timestampedBy, timestampValidUntil);
    }
}
