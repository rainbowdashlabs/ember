/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A mailbox a station watches for paperwork.
 *
 * <p>The password travels as an {@link EncryptedBlob} and is never held here in the clear: the one
 * place it exists as text is inside the method that opens the connection.
 *
 * @param id              the mailbox identifier
 * @param stationId       the station whose mailbox this is
 * @param name            what the station calls it, so a page listing several can tell them apart
 * @param host            the mail server
 * @param port            the port on that server
 * @param security        how the connection is secured
 * @param username        the account the connection signs in as
 * @param password        the password, encrypted with the instance credential key
 * @param folder          which folder is watched
 * @param enabled         whether the poller visits it
 * @param intervalMinutes how often it is checked, never below the floor the operator set
 * @param importFrom      mail older than this is ignored, which is what stops a first cycle importing a decade
 * @param lastCheckAt     when the poller last visited, or null before the first time
 * @param lastError       what went wrong last, or null where nothing has
 * @param failureCount    consecutive failures, which drive the backoff and the suspension
 * @param suspended       whether repeated failure took it out of the rotation
 * @param createdAt       when it was added
 */
public record MailMailbox(
        int id,
        int stationId,
        String name,
        String host,
        int port,
        MailSecurity security,
        String username,
        EncryptedBlob password,
        String folder,
        boolean enabled,
        int intervalMinutes,
        Instant importFrom,
        Instant lastCheckAt,
        String lastError,
        int failureCount,
        boolean suspended,
        Instant createdAt) {

    public static RowMapping<MailMailbox> map() {
        return row -> new MailMailbox(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("host"),
                row.getInt("port"),
                MailSecurity.valueOf(row.getString("security")),
                row.getString("username"),
                new EncryptedBlob(row.getBytes("password_iv"), row.getBytes("password_ciphertext")),
                row.getString("folder"),
                row.getBoolean("enabled"),
                row.getInt("interval_minutes"),
                row.get("import_from", INSTANT_TIMESTAMP),
                row.get("last_check_at", INSTANT_TIMESTAMP),
                row.getString("last_error"),
                row.getInt("failure_count"),
                row.getBoolean("suspended"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
