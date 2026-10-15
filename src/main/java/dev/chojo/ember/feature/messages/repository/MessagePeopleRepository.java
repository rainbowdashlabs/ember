/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.ReachablePerson;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Who may write to whom, and what an account allows about its messages.
 *
 * <p>Two accounts reach each other when they are current members of at least one common station.
 * An account with a guardian at any station has messages only once a guardian turned them on; one
 * whose last guardian went is like everybody else again, which is why the guardian is asked for on
 * every read rather than remembered.
 */
@Singleton
public class MessagePeopleRepository {

    /**
     * The condition an account aliased {@code a} meets when it can use messages: it has no current
     * guardian anywhere, or a guardian turned messages on for it.
     */
    private static final String USABLE = """
            (a.messages_guardian_allowed OR NOT EXISTS (
                SELECT 1
                FROM station_member managed
                JOIN member_manager mm ON mm.managed_id = managed.id
                JOIN station_member guardian ON guardian.id = mm.manager_id
                WHERE managed.account_id = a.id AND managed.former = FALSE AND guardian.former = FALSE))""";

    /**
     * Whether an account can use messages.
     *
     * @param accountId the account
     * @return false for an account with a guardian who has not turned them on, and for no account at all
     */
    public boolean usable(int accountId) {
        return query("""
                SELECT %s AS usable FROM account a WHERE a.id = :account_id;""", USABLE)
                .single(call().bind("account_id", accountId))
                .map(row -> row.getBoolean("usable"))
                .first()
                .orElse(false);
    }

    /**
     * Whether one account may write to another: they share a current station, the other can use
     * messages, and the other has not blocked the writer.
     *
     * @param writerId the account writing
     * @param targetId the account written to
     * @return whether the writer reaches the target
     */
    public boolean reaches(int writerId, int targetId) {
        return query("""
                SELECT EXISTS(
                    SELECT 1
                    FROM station_member me
                    JOIN station_member other
                        ON other.station_id = me.station_id AND other.former = FALSE AND other.account_id = :target_id
                    JOIN account a ON a.id = other.account_id
                    WHERE me.account_id = :writer_id AND me.former = FALSE AND :writer_id <> :target_id
                    AND NOT EXISTS (
                        SELECT 1 FROM account_block b
                        WHERE b.blocker_account_id = a.id AND b.blocked_account_id = :writer_id)
                    AND %s
                ) AS reaches;""", USABLE)
                .single(call().bind("writer_id", writerId).bind("target_id", targetId))
                .map(row -> row.getBoolean("reaches"))
                .first()
                .orElse(false);
    }

    /**
     * The people an account may write to whose name holds the search, by name, each with one
     * station they share.
     *
     * @param accountId the account looking
     * @param search    part of a name, or blank for everybody
     * @param limit     how many to answer at most
     * @return the people found
     */
    public List<ReachablePerson> searchReachable(int accountId, String search, int limit) {
        return query("""
                SELECT * FROM (
                    SELECT DISTINCT ON (a.id)
                        a.id AS person_account_id, a.uid AS person_uid,
                        a.first_name AS person_first_name, a.last_name AS person_last_name,
                        s.name AS station_name
                    FROM station_member me
                    JOIN station_member other
                        ON other.station_id = me.station_id AND other.former = FALSE
                        AND other.account_id IS NOT NULL AND other.account_id <> me.account_id
                    JOIN account a ON a.id = other.account_id
                    JOIN station s ON s.id = me.station_id
                    WHERE me.account_id = :account_id AND me.former = FALSE
                    AND (a.first_name || ' ' || a.last_name) ILIKE :pattern
                    AND NOT EXISTS (
                        SELECT 1 FROM account_block b
                        WHERE b.blocker_account_id = a.id AND b.blocked_account_id = :account_id)
                    AND %s
                    ORDER BY a.id, s.name
                ) found
                ORDER BY lower(person_first_name), lower(person_last_name), person_account_id
                LIMIT :limit;""", USABLE)
                .single(call().bind("account_id", accountId)
                        .bind("pattern", "%" + search.strip() + "%")
                        .bind("limit", limit))
                .map(ReachablePerson.map())
                .all();
    }

    /**
     * An account by its identity.
     *
     * @param uid the account's identity
     * @return the person, or empty where no account carries it
     */
    public Optional<Person> findByUid(UUID uid) {
        return query("""
                SELECT a.id AS account_id, a.uid, a.first_name, a.last_name
                FROM account a WHERE a.uid = :uid::uuid;""")
                .single(call().bind("uid", uid, UUID_STRING))
                .map(row -> Person.require(row, ""))
                .first();
    }

    /**
     * Whether a guardian turned messages on for an account.
     *
     * @param accountId the account
     * @return the flag, false for no account
     */
    public boolean guardianAllowed(int accountId) {
        return query("""
                SELECT messages_guardian_allowed FROM account WHERE id = :account_id;""")
                .single(call().bind("account_id", accountId))
                .map(row -> row.getBoolean("messages_guardian_allowed"))
                .first()
                .orElse(false);
    }

    /**
     * Turns messages on or off for an account on behalf of its guardians.
     *
     * @param accountId the account
     * @param allowed   whether it may use messages
     */
    public void setGuardianAllowed(int accountId, boolean allowed) {
        query("""
                UPDATE account SET messages_guardian_allowed = :allowed WHERE id = :account_id;""")
                .single(call().bind("account_id", accountId).bind("allowed", allowed))
                .update();
    }

    /**
     * Whether an account wants mail about unread messages.
     *
     * @param accountId the account
     * @return the setting, false for no account
     */
    public boolean mailWanted(int accountId) {
        return query("""
                SELECT messages_mail FROM account WHERE id = :account_id;""")
                .single(call().bind("account_id", accountId))
                .map(row -> row.getBoolean("messages_mail"))
                .first()
                .orElse(false);
    }

    /**
     * Turns the mail about unread messages on or off for an account.
     *
     * @param accountId the account
     * @param wanted    whether it wants the mail
     */
    public void setMailWanted(int accountId, boolean wanted) {
        query("""
                UPDATE account SET messages_mail = :wanted WHERE id = :account_id;""")
                .single(call().bind("account_id", accountId).bind("wanted", wanted))
                .update();
    }
}
