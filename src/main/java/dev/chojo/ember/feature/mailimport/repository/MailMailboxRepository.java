/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.repository;

import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The mailboxes stations watch for paperwork.
 */
@Singleton
public class MailMailboxRepository {

    private static final String COLUMNS = """
            id, station_id, name, host, port, security, username, password_iv, password_ciphertext, folder,
            verify_dkim, enabled, interval_minutes, import_from, last_check_at, last_error, failure_count, suspended,
            created_at""";

    /**
     * Writes a mailbox, whose password the caller has already encrypted.
     *
     * @param password the password as ciphertext. Nothing here ever sees it in the clear
     * @return the mailbox as it was written
     */
    public MailMailbox create(
            int stationId,
            String name,
            String host,
            int port,
            MailSecurity security,
            String username,
            EncryptedBlob password,
            String folder,
            boolean verifyDkim,
            int intervalMinutes,
            Instant importFrom) {
        return query("""
                        INSERT INTO mail_mailbox(station_id, name, host, port, security, username, password_iv,
                                                 password_ciphertext, folder, verify_dkim, interval_minutes,
                                                 import_from)
                        VALUES (:station_id, :name, :host, :port, :security, :username, :password_iv,
                                :password_ciphertext, :folder, :verify_dkim, :interval_minutes, :import_from)
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("host", host)
                        .bind("port", port)
                        .bind("security", security.name())
                        .bind("username", username)
                        .bind("password_iv", password.iv())
                        .bind("password_ciphertext", password.ciphertext())
                        .bind("folder", folder)
                        .bind("verify_dkim", verifyDkim)
                        .bind("interval_minutes", intervalMinutes)
                        .bind("import_from", importFrom, INSTANT_TIMESTAMP))
                .map(MailMailbox.map())
                .first()
                .orElseThrow();
    }

    public Optional<MailMailbox> findById(int id) {
        return query("SELECT %s FROM mail_mailbox WHERE id = :id;", COLUMNS)
                .single(call().bind("id", id))
                .map(MailMailbox.map())
                .first();
    }

    public List<MailMailbox> findByStation(int stationId) {
        return query("SELECT %s FROM mail_mailbox WHERE station_id = :station_id ORDER BY id;", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(MailMailbox.map())
                .all();
    }

    /**
     * Every mailbox the poller should consider, which is the enabled ones that are not suspended.
     *
     * <p>Whether one is due is not asked here: that depends on its interval and on the floor the
     * operator set, which is the scheduler's business rather than a query's.
     */
    public List<MailMailbox> findPollable() {
        return query("""
                        SELECT %s FROM mail_mailbox
                        WHERE enabled = TRUE AND suspended = FALSE
                        ORDER BY COALESCE(last_check_at, to_timestamp(0)), id;""", COLUMNS).single().map(MailMailbox.map()).all();
    }

    /**
     * Changes everything about a mailbox except its password, which has its own method so that a rewrite
     * of the connection settings cannot quietly clear a credential it was never given.
     */
    public boolean update(
            int id,
            String name,
            String host,
            int port,
            MailSecurity security,
            String username,
            String folder,
            boolean verifyDkim,
            boolean enabled,
            int intervalMinutes,
            Instant importFrom) {
        return query("""
                        UPDATE mail_mailbox
                        SET name = :name, host = :host, port = :port, security = :security, username = :username,
                            folder = :folder, verify_dkim = :verify_dkim, enabled = :enabled,
                            interval_minutes = :interval_minutes, import_from = :import_from
                        WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("host", host)
                        .bind("port", port)
                        .bind("security", security.name())
                        .bind("username", username)
                        .bind("folder", folder)
                        .bind("verify_dkim", verifyDkim)
                        .bind("enabled", enabled)
                        .bind("interval_minutes", intervalMinutes)
                        .bind("import_from", importFrom, INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /** Writes a new password over the old one, already encrypted. */
    public boolean updatePassword(int id, EncryptedBlob password) {
        return query("""
                        UPDATE mail_mailbox
                        SET password_iv = :password_iv, password_ciphertext = :password_ciphertext
                        WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("password_iv", password.iv())
                        .bind("password_ciphertext", password.ciphertext()))
                .update()
                .changed();
    }

    /**
     * Writes down that a cycle succeeded, which clears the failures and whatever error was standing.
     */
    public void recordSuccess(int id, Instant at) {
        query("""
                        UPDATE mail_mailbox
                        SET last_check_at = :at, last_error = NULL, failure_count = 0
                        WHERE id = :id;""")
                .single(call().bind("id", id).bind("at", at, INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Writes down that a cycle failed and counts it.
     *
     * <p>The error stays on the mailbox rather than only in a log file, because the question a station
     * asks is why their import stopped and the answer has to be on the page they are looking at.
     */
    public void recordFailure(int id, Instant at, String error) {
        query("""
                        UPDATE mail_mailbox
                        SET last_check_at = :at, last_error = :error, failure_count = failure_count + 1
                        WHERE id = :id;""")
                .single(call().bind("id", id).bind("at", at, INSTANT_TIMESTAMP).bind("error", error))
                .update();
    }

    /** Takes a mailbox out of the rotation after it has failed enough times to stop being worth trying. */
    public void suspend(int id) {
        query("UPDATE mail_mailbox SET suspended = TRUE WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /** Puts a suspended mailbox back in, which is what somebody does after fixing whatever it was. */
    public boolean resume(int id) {
        return query("UPDATE mail_mailbox SET suspended = FALSE, failure_count = 0, last_error = NULL WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return query("DELETE FROM mail_mailbox WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }
}
