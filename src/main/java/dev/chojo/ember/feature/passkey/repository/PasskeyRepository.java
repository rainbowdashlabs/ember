/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.repository;

import dev.chojo.ember.feature.passkey.entity.PasskeyListEntry;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The passkey view of the credential tables: only rows that may start a sign-in. The tables
 * themselves belong to the two-factor feature; what this repository adds is the questions only
 * passkeys ask of them.
 */
@Singleton
public class PasskeyRepository {

    /**
     * The account's active sign-in passkeys, as the member's list shows them.
     */
    public List<PasskeyListEntry> listForAccount(int accountId) {
        return query("""
                SELECT f.id, f.label, f.created_at, f.last_used_at, w.aaguid, w.second_factor, w.credential_id, w.user_handle
                FROM account_2fa_factor f
                JOIN account_2fa_webauthn w ON w.factor_id = f.id
                WHERE f.account_id = :account_id AND f.disabled_at IS NULL AND w.sign_in
                ORDER BY f.created_at;""")
                .single(call().bind("account_id", accountId))
                .map(PasskeyListEntry.map())
                .all();
    }

    /**
     * One of the account's active sign-in passkeys by factor id, or empty when the id belongs
     * to somebody else, to a second-factor credential, or to nothing.
     */
    public Optional<PasskeyListEntry> findForAccount(int accountId, int factorId) {
        return query("""
                SELECT f.id, f.label, f.created_at, f.last_used_at, w.aaguid, w.second_factor, w.credential_id, w.user_handle
                FROM account_2fa_factor f
                JOIN account_2fa_webauthn w ON w.factor_id = f.id
                WHERE f.id = :factor_id AND f.account_id = :account_id AND f.disabled_at IS NULL AND w.sign_in;""")
                .single(call().bind("factor_id", factorId).bind("account_id", accountId))
                .map(PasskeyListEntry.map())
                .first();
    }

    public int countActiveForAccount(int accountId) {
        return query("""
                SELECT COUNT(*) AS held
                FROM account_2fa_factor f
                JOIN account_2fa_webauthn w ON w.factor_id = f.id
                WHERE f.account_id = :account_id AND f.disabled_at IS NULL AND w.sign_in;""")
                .single(call().bind("account_id", accountId))
                .map(row -> row.getInt("held"))
                .first()
                .orElse(0);
    }

    /**
     * Whether the account holds a sign-in passkey that has completed a sign-in ceremony. This
     * is what retiring a password and switching password sign-in off read as evidence: a
     * passkey created in an offer screen and never exercised proves nothing.
     */
    public boolean hasTriedSignInPasskey(int accountId) {
        return query("""
                SELECT EXISTS(
                    SELECT 1
                    FROM account_2fa_factor f
                    JOIN account_2fa_webauthn w ON w.factor_id = f.id
                    WHERE f.account_id = :account_id AND f.disabled_at IS NULL AND w.sign_in
                    AND f.last_used_at IS NOT NULL
                ) AS tried;""")
                .single(call().bind("account_id", accountId))
                .map(row -> row.getBoolean("tried"))
                .first()
                .orElse(false);
    }

    /**
     * Opts the account's sign-in passkeys into or out of the password path (the member's own
     * "ask for the passkey after my password" switch). Only ever touches sign-in credentials;
     * a plain security key's flag is not this switch's to change.
     */
    public boolean setSecondFactorForSignInPasskeys(int accountId, boolean enabled) {
        return query("""
                UPDATE account_2fa_webauthn w
                SET second_factor = :enabled
                FROM account_2fa_factor f
                WHERE f.id = w.factor_id AND f.account_id = :account_id AND f.disabled_at IS NULL AND w.sign_in;""")
                .single(call().bind("account_id", accountId).bind("enabled", enabled))
                .update()
                .changed();
    }

    /**
     * Disables every credential that may start a sign-in, and only those: the onboard-again
     * button wipes the passkeys but leaves second factors alone, because the member it rescues
     * may still know their authenticator app perfectly well.
     */
    public int disableSignInPasskeys(int accountId) {
        return query("""
                UPDATE account_2fa_factor f
                SET disabled_at = now()
                FROM account_2fa_webauthn w
                WHERE w.factor_id = f.id AND f.account_id = :account_id AND f.disabled_at IS NULL AND w.sign_in;""").single(call().bind("account_id", accountId)).update().rows();
    }

    /**
     * The accounts whose password may be retired: they hold one, and a passkey that has
     * completed a sign-in ceremony stands beside it. The rope comes away only from somebody
     * already holding the other one.
     */
    public List<Integer> listRetireEligibleAccounts() {
        return query("""
                SELECT c.account_id
                FROM account_credential c
                WHERE EXISTS (
                    SELECT 1 FROM account_2fa_factor f
                    JOIN account_2fa_webauthn w ON w.factor_id = f.id
                    WHERE f.account_id = c.account_id AND f.disabled_at IS NULL AND w.sign_in
                    AND f.last_used_at IS NOT NULL
                );""").single(call()).map(row -> row.getInt("account_id")).all();
    }

    /**
     * The residue: password holders with no exercised passkey, which is the group that cannot
     * move yet. A list rather than an automatic decision, because all three answers to it are
     * decisions a person makes about somebody they know.
     */
    public List<ResidueEntry> listResidue() {
        return query("""
                SELECT a.id, a.first_name, a.last_name, a.last_sign_in_at,
                       (a.email IS NOT NULL AND a.email != '' AND a.email NOT LIKE '%.local') AS reachable,
                       EXISTS (SELECT 1 FROM member_manager mm
                               JOIN station_member sm ON sm.id = mm.managed_id
                               WHERE sm.account_id = a.id) AS has_guardian
                FROM account a
                JOIN account_credential c ON c.account_id = a.id
                WHERE NOT EXISTS (
                    SELECT 1 FROM account_2fa_factor f
                    JOIN account_2fa_webauthn w ON w.factor_id = f.id
                    WHERE f.account_id = a.id AND f.disabled_at IS NULL AND w.sign_in
                    AND f.last_used_at IS NOT NULL
                )
                ORDER BY a.last_sign_in_at DESC NULLS LAST;""")
                .single(call())
                .map(row -> new ResidueEntry(
                        row.getInt("id"),
                        row.getString("first_name"),
                        row.getString("last_name"),
                        row.get("last_sign_in_at", INSTANT_TIMESTAMP),
                        row.getBoolean("reachable"),
                        row.getBoolean("has_guardian")))
                .all();
    }

    /**
     * @param reachable whether mail to the member's own address can arrive
     * @param hasGuardian whether somebody manages the member and can hold up the QR code
     */
    public record ResidueEntry(
            int accountId,
            String firstName,
            String lastName,
            Instant lastSignInAt,
            boolean reachable,
            boolean hasGuardian) {}

    /**
     * The account's answer to the one-time passkey offer, or empty when it was never answered.
     */
    public Optional<OfferAnswer> findOfferAnswer(int accountId) {
        return query("SELECT passkey_offer_answered_at, passkey_offer_declined FROM account WHERE id = :id;")
                .single(call().bind("id", accountId))
                .map(row -> new OfferAnswer(
                        row.get("passkey_offer_answered_at", INSTANT_TIMESTAMP),
                        row.getBoolean("passkey_offer_declined")))
                .first()
                .filter(answer -> answer.answeredAt() != null);
    }

    /**
     * Stores the member's answer. "Later" stamps the time so the offer returns after thirty
     * days; "no thanks" additionally sets the flag that ends it for good. The answer lives on
     * the account, so an answer given on the phone is honoured on the laptop.
     */
    public boolean answerOffer(int accountId, boolean declined) {
        return query("""
                UPDATE account
                SET passkey_offer_answered_at = now(), passkey_offer_declined = :declined
                WHERE id = :id;""")
                .single(call().bind("id", accountId).bind("declined", declined))
                .update()
                .changed();
    }

    public record OfferAnswer(Instant answeredAt, boolean declined) {}

    // -- What the operator sees --

    /**
     * The three numbers the operator reads, because they mean different things: adoption is the
     * first, the size of the remaining rope the second, and the group that cannot move yet the
     * third.
     */
    public AdoptionFigures adoptionFigures() {
        return query("""
                SELECT
                    (SELECT COUNT(DISTINCT f.account_id)
                     FROM account_2fa_factor f
                     JOIN account_2fa_webauthn w ON w.factor_id = f.id
                     WHERE f.disabled_at IS NULL AND w.sign_in AND f.last_used_at IS NOT NULL) AS with_tried_passkey,
                    (SELECT COUNT(*) FROM account_credential) AS with_password,
                    (SELECT COUNT(*)
                     FROM account_credential c
                     WHERE NOT EXISTS (
                         SELECT 1 FROM account_2fa_factor f
                         JOIN account_2fa_webauthn w ON w.factor_id = f.id
                         WHERE f.account_id = c.account_id AND f.disabled_at IS NULL AND w.sign_in
                     )) AS with_password_and_no_passkey;""")
                .single(call())
                .map(row -> new AdoptionFigures(
                        row.getInt("with_tried_passkey"),
                        row.getInt("with_password"),
                        row.getInt("with_password_and_no_passkey")))
                .first()
                .orElse(new AdoptionFigures(0, 0, 0));
    }

    /**
     * How many accounts have no way in without a passkey: they hold one, and their password
     * sign-in is off or they hold no password at all. This is the count that refuses lowering
     * the mode below ENCOURAGED, where the login screen would stop offering the passkey path.
     */
    public int countAccountsDependingOnPasskey() {
        return query("""
                SELECT COUNT(DISTINCT f.account_id) AS depending
                FROM account_2fa_factor f
                JOIN account_2fa_webauthn w ON w.factor_id = f.id
                LEFT JOIN account_credential c ON c.account_id = f.account_id
                WHERE f.disabled_at IS NULL AND w.sign_in
                AND (c.account_id IS NULL OR c.password_login_disabled_at IS NOT NULL);""")
                .single(call())
                .map(row -> row.getInt("depending"))
                .first()
                .orElse(0);
    }

    /**
     * The report an operator reads before switching to the passwordless mode: what would
     * actually happen, counted rather than promised. Every figure is about the accounts that
     * hold a password today, because those are the ones the switch does not touch.
     */
    public PasswordlessReport passwordlessReport() {
        return query("""
                SELECT
                    COUNT(*) AS password_holders,
                    COUNT(*) FILTER (WHERE NOT has_passkey) AS without_passkey,
                    COUNT(*) FILTER (WHERE NOT has_passkey AND NOT reachable AND NOT has_guardian) AS qr_only,
                    COUNT(*) FILTER (WHERE last_sign_in IS NULL OR last_sign_in < now() - INTERVAL '1 year') AS dormant
                FROM (
                    SELECT a.last_sign_in_at AS last_sign_in,
                           (a.email IS NOT NULL AND a.email != '' AND a.email NOT LIKE '%.local') AS reachable,
                           EXISTS (SELECT 1 FROM account_2fa_factor f
                                   JOIN account_2fa_webauthn w ON w.factor_id = f.id
                                   WHERE f.account_id = a.id AND f.disabled_at IS NULL AND w.sign_in) AS has_passkey,
                           EXISTS (SELECT 1 FROM member_manager mm
                                   JOIN station_member sm ON sm.id = mm.managed_id
                                   WHERE sm.account_id = a.id) AS has_guardian
                    FROM account a
                    JOIN account_credential c ON c.account_id = a.id
                ) holders;""")
                .single(call())
                .map(row -> new PasswordlessReport(
                        row.getInt("password_holders"),
                        row.getInt("without_passkey"),
                        row.getInt("qr_only"),
                        row.getInt("dormant")))
                .first()
                .orElse(new PasswordlessReport(0, 0, 0, 0));
    }

    /**
     * @param accountsWithTriedPasskey accounts holding a passkey that has completed a sign-in
     * @param accountsWithPassword accounts still holding a password
     * @param accountsWithPasswordAndNoPasskey the group that cannot move yet
     */
    public record AdoptionFigures(
            int accountsWithTriedPasskey, int accountsWithPassword, int accountsWithPasswordAndNoPasskey) {}

    /**
     * @param wouldKeepPassword accounts that keep their password when the mode switches
     * @param withoutPasskey of those, how many hold no passkey at all
     * @param reachableOnlyByQr of those, how many have neither a reachable address nor a
     *         guardian, which the QR code in the room is the only thing that gets to
     * @param dormantForAYear password holders who have not signed in for a year
     */
    public record PasswordlessReport(
            int wouldKeepPassword, int withoutPasskey, int reachableOnlyByQr, int dormantForAYear) {}
}
