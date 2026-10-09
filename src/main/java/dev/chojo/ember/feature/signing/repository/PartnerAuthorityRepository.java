/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import dev.chojo.ember.feature.signing.entity.PinnedAuthority;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The signing authorities of federation partners, pinned to the partnerships.
 *
 * <p>Pins are only ever added and updated, never removed here; a pin goes with its partnership. Whether a
 * new pin is the partnership's first is decided by the insert itself, so the first statement taken from a
 * partner pins with {@code FIRST_FETCH} and every later one with {@code ANNOUNCED}.
 */
@Singleton
public class PartnerAuthorityRepository {
    private static final String PIN_COLUMNS = """
            ca.partner_id,
            fp.partner_station_id,
            fp.partner_station_name,
            ca.sha256,
            ca.certificate,
            ca.active,
            ca.pin_kind,
            ca.pinned_at,
            ca.last_listed_at,
            ca.crl,
            ca.crl_next_update""";

    /**
     * @param partnerId the partnership
     * @return every authority pinned to it, the earliest pinned first
     */
    public List<PinnedAuthority> pinnedFor(int partnerId) {
        return query("""
                        SELECT
                            %s
                        FROM
                            federation_partner_signing_ca ca
                            JOIN federation_partner fp ON fp.id = ca.partner_id
                        WHERE ca.partner_id = :partner_id
                        ORDER BY ca.id;""", PIN_COLUMNS)
                .single(call().bind("partner_id", partnerId))
                .map(PinnedAuthority.map())
                .all();
    }

    /** @return every authority pinned to any partnership of any station here, the earliest pinned first */
    public List<PinnedAuthority> allPinned() {
        return query("""
                        SELECT
                            %s
                        FROM
                            federation_partner_signing_ca ca
                            JOIN federation_partner fp ON fp.id = ca.partner_id
                        ORDER BY ca.id;""", PIN_COLUMNS).single(call()).map(PinnedAuthority.map()).all();
    }

    /** @return the partnerships that have at least one authority pinned, active or paused, by id */
    public List<Integer> partnersWithPins() {
        return query("SELECT DISTINCT partner_id FROM federation_partner_signing_ca ORDER BY partner_id;")
                .single(call())
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Pins an authority to a partnership, unless it is pinned there already.
     *
     * @param partnerId the partnership
     * @param pin       the authority and the statement it was taken from
     * @return true when this call pinned it
     */
    public boolean pin(int partnerId, NewPin pin) {
        return query("""
                        INSERT INTO federation_partner_signing_ca (partner_id, sha256, certificate, active, pin_kind,
                                                                   pinned_at, pinned_statement, pinned_signature,
                                                                   last_listed_at)
                        VALUES (:partner_id, :certificate_hash, :certificate, :active,
                                CASE
                                    WHEN EXISTS (SELECT 1 FROM federation_partner_signing_ca
                                                 WHERE partner_id = :partner_id) THEN 'ANNOUNCED'
                                    ELSE 'FIRST_FETCH'
                                END,
                                :at, :statement, :signature, :at)
                        ON CONFLICT (partner_id, sha256) DO NOTHING;""")
                .single(call().bind("partner_id", partnerId)
                        .bind("certificate_hash", pin.sha256())
                        .bind("certificate", pin.certificate())
                        .bind("active", pin.active())
                        .bind("at", pin.at(), INSTANT_TIMESTAMP)
                        .bind("statement", pin.statement())
                        .bind("signature", pin.signature()))
                .insert()
                .changed();
    }

    /**
     * Records that a statement named a pinned authority again.
     *
     * @param partnerId the partnership
     * @param sha256    the authority's certificate hash
     * @param active    whether the statement named it as the one issuing new certificates
     * @param at        when the statement was taken
     */
    public void listed(int partnerId, String sha256, boolean active, Instant at) {
        query("""
                        UPDATE federation_partner_signing_ca
                        SET active         = :active,
                            last_listed_at = :at
                        WHERE partner_id = :partner_id
                          AND sha256 = :certificate_hash;""")
                .single(call().bind("partner_id", partnerId)
                        .bind("certificate_hash", sha256)
                        .bind("active", active)
                        .bind("at", at, INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Takes in a pinned authority's revocation list, unless the stored one was issued later.
     *
     * @param partnerId  the partnership
     * @param sha256     the authority's certificate hash
     * @param list       the list, DER encoded, checked against the authority
     * @param thisUpdate when the list was issued
     * @param nextUpdate when it says the next one is due
     * @return true when the list was taken
     */
    public boolean takeRevocationList(
            int partnerId, String sha256, byte[] list, Instant thisUpdate, Instant nextUpdate) {
        return query("""
                        UPDATE federation_partner_signing_ca
                        SET crl             = :crl,
                            crl_this_update = :this_update,
                            crl_next_update = :next_update
                        WHERE partner_id = :partner_id
                          AND sha256 = :certificate_hash
                          AND (crl_this_update IS NULL OR crl_this_update <= :this_update);""")
                .single(call().bind("partner_id", partnerId)
                        .bind("certificate_hash", sha256)
                        .bind("crl", list)
                        .bind("this_update", thisUpdate, INSTANT_TIMESTAMP)
                        .bind("next_update", nextUpdate, INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * An authority to pin and the statement it comes from.
     *
     * @param sha256      SHA-256 of the certificate, lower-case hexadecimal
     * @param certificate the certificate, DER encoded
     * @param active      whether the statement named it as the one issuing new certificates
     * @param at          when the statement was taken
     * @param statement   the statement's text, as signed
     * @param signature   the partner station's signature over it
     */
    public record NewPin(
            String sha256, byte[] certificate, boolean active, Instant at, String statement, String signature) {}
}
