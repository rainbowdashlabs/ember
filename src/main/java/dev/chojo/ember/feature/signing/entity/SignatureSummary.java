/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * How the signatures asked for on one document stand, as a list shows it: the newest request on the document
 * and how many of its fields are signed, still open, or open with nobody who could sign them.
 *
 * @param requestUid    the request
 * @param state         where the request stands
 * @param signed        how many fields were signed, electronically or confirmed on paper
 * @param expected      how many fields still count, which leaves out the waived and withdrawn ones
 * @param open          how many fields still wait for a signature
 * @param nobodyCanSign how many of the open fields nobody can sign: a guardian place nobody holds, a document
 *                      without an issuer, a signer who was deleted, or a member without guardians who has to
 *                      sign through one
 */
public record SignatureSummary(
        UUID requestUid, RequestState state, int signed, int expected, int open, int nobodyCanSign) {

    /**
     * Reads a summary from a row carrying the request's {@code uid} and {@code state} and the counts
     * {@code signed}, {@code expected}, {@code open} and {@code nobody_can_sign}.
     *
     * @param row the row
     * @return the summary
     * @throws SQLException when a column cannot be read
     */
    public static SignatureSummary read(Row row) throws SQLException {
        return new SignatureSummary(
                row.get("uid", UUID_STRING),
                row.getEnum("state", RequestState.class),
                row.getInt("signed"),
                row.getInt("expected"),
                row.getInt("open"),
                row.getInt("nobody_can_sign"));
    }
}
