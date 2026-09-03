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
                SELECT f.id, f.label, f.created_at, f.last_used_at, w.aaguid, w.second_factor
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
                SELECT f.id, f.label, f.created_at, f.last_used_at, w.aaguid, w.second_factor
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
}
