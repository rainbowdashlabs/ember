/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.devicerequest.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StepUpCategory;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

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
 * @param namedAccountId the account the requesting device asked for, claimed and never proved, so
 *         it decides who may approve and never who is signed in. {@code null} where the identifier
 *         matched nothing, which is a request nobody can approve
 * @param matchNumber the number the requesting screen shows and the approving screen asks for
 * @param matchChoices the six numbers the approval screen offers, in the order it offers them
 * @param rejectedAt when the approving screen picked the wrong number, which ends the request
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
        Integer namedAccountId,
        StepUpCategory stepUpCategory,
        Instant approvedAt,
        Instant consumedAt,
        Instant expiresAt,
        Instant rejectedAt,
        int matchNumber,
        List<Integer> matchChoices,
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
                row.getObject("named_account_id", Integer.class),
                row.getEnum("step_up_category", StepUpCategory.class),
                row.get("approved_at", INSTANT_TIMESTAMP),
                row.get("consumed_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("rejected_at", INSTANT_TIMESTAMP),
                row.getInt("match_number"),
                elements(row.getArray("match_choices"), Integer[].class),
                row.getInt("attempts"),
                row.getString("requested_user_agent"),
                row.getString("requested_country"),
                row.getBoolean("claim_token_issued"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    private static <T> List<T> elements(Array array, Class<T[]> type) throws SQLException {
        return List.of(type.cast(array.getArray()));
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isApproved() {
        return approvedAt != null;
    }

    /** Whether the approving screen picked the wrong number, which ends the request for good. */
    public boolean isRejected() {
        return rejectedAt != null;
    }

    /**
     * Whether this reader may be shown the request at all.
     *
     * <p>A request that names no account was raised for an identifier that matched nothing. It is
     * answered like any other so that the screen cannot be asked which addresses exist, and it
     * reaches this point belonging to nobody, so nobody passes.
     */
    public boolean namesAccount(int accountId) {
        return namedAccountId != null && namedAccountId == accountId;
    }

    /** Whether this is the purpose given, which is what every branch asks before it acts. */
    public boolean is(DeviceRequestPurpose other) {
        return purpose == other;
    }
}
