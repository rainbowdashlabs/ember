/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.devicerequest.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StepUpCategory;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A device asking one that is already signed in to vouch for it: the asking device holds the poll
 * secret, an old, signed-in one confirms the code, and the poll that follows hands over a token that
 * may be spent exactly once on whatever {@link #purpose} says. The hashes never leave the database;
 * what travels is only ever the raw value each side already holds.
 *
 * @param purpose what approving this buys, which every guarded update must name
 * @param approvedAccountId the account whose session approved the request, or {@code null}
 * @param subjectAccountId the account the grant is for, written at approval. The approver, except
 *         where a guardian signed in a member in their care
 * @param requestingAccountId the account that raised a step-up request, {@code null} for the
 *         purposes an unidentified device raises
 * @param requestingSessionId the session a successful step-up stamps, {@code null} otherwise
 * @param stepUpCategory what the step-up was demanded for, so the approval screen can say, and the
 *         only thing an approval answers
 * @param claimTokenIssued whether the one-time token was already handed out; it is delivered exactly
 *         once, on the first poll after the approval
 */
public record DeviceRequest(
        int id,
        DeviceRequestPurpose purpose,
        Integer approvedAccountId,
        Integer subjectAccountId,
        Integer requestingAccountId,
        Integer requestingSessionId,
        StepUpCategory stepUpCategory,
        Instant approvedAt,
        Instant consumedAt,
        Instant expiresAt,
        int attempts,
        String requestedUserAgent,
        String requestedCountry,
        boolean claimTokenIssued,
        Instant createdAt) {

    public static RowMapping<DeviceRequest> map() {
        return row -> new DeviceRequest(
                row.getInt("id"),
                row.getEnum("purpose", DeviceRequestPurpose.class),
                row.getObject("approved_account_id", Integer.class),
                row.getObject("subject_account_id", Integer.class),
                row.getObject("requesting_account_id", Integer.class),
                row.getObject("requesting_session_id", Integer.class),
                row.getEnum("step_up_category", StepUpCategory.class),
                row.get("approved_at", INSTANT_TIMESTAMP),
                row.get("consumed_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.getInt("attempts"),
                row.getString("requested_user_agent"),
                row.getString("requested_country"),
                row.getBoolean("claim_token_issued"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isApproved() {
        return approvedAt != null;
    }

    /** Whether this is the purpose given, which is what every branch asks before it acts. */
    public boolean is(DeviceRequestPurpose other) {
        return purpose == other;
    }
}
