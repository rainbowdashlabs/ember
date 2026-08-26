/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

/**
 * What one station may keep, resolved: every dimension with its number and where the number came from.
 *
 * <p>Resolved rather than raw, because three parties can have a say and the answer to "how much may this
 * station keep" is one number rather than three nullable ones. The origin travels with it so a screen can
 * tell a granted number from an inherited one, which is the difference somebody looking at it needs.
 *
 * @param stationId the station
 * @param authority who pays for its storage, and therefore what stands behind an unset dimension
 * @param total     how much it may keep in all
 * @param kb        how much of that may be knowledge base files and the documents filed beside them
 * @param board     how much of that may be board attachments
 * @param images    how much of that may be images
 * @param pages     how much of that may be page media
 * @param perFile   the largest single file
 * @param perImage  the largest single image
 */
public record StationQuotas(
        int stationId,
        QuotaAuthority authority,
        ResolvedQuota total,
        ResolvedQuota kb,
        ResolvedQuota board,
        ResolvedQuota images,
        ResolvedQuota pages,
        ResolvedQuota perFile,
        ResolvedQuota perImage) {

    /**
     * One dimension, resolved.
     *
     * @param bytes  the limit in bytes, {@link Long#MAX_VALUE} when there is none
     * @param origin who set it
     */
    public record ResolvedQuota(long bytes, QuotaOrigin origin) {
        public static ResolvedQuota unlimited() {
            return new ResolvedQuota(Long.MAX_VALUE, QuotaOrigin.UNLIMITED);
        }
    }
}
