/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.repository;

import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** The installation's one push key pair. */
@Singleton
public class VapidKeyRepository {

    /**
     * The key pair as it is stored.
     *
     * @param publicKey         the public key as an uncompressed point
     * @param wrappedPrivateKey the private key, wrapped under the at-rest secret
     */
    public record StoredVapidKey(byte[] publicKey, byte[] wrappedPrivateKey) {}

    /** @return the stored key pair, empty before the first one was made */
    public Optional<StoredVapidKey> find() {
        return query("SELECT public_key, wrapped_private_key FROM push_vapid_key WHERE id = 1;")
                .single(call())
                .map(row -> new StoredVapidKey(row.getBytes("public_key"), row.getBytes("wrapped_private_key")))
                .first();
    }

    /**
     * Stores a key pair unless one is stored already, so two nodes starting together keep the same
     * one: whoever writes second keeps reading the first.
     *
     * @return true when this call stored it
     */
    public boolean storeIfAbsent(byte[] publicKey, byte[] wrappedPrivateKey) {
        return query("""
                        INSERT INTO push_vapid_key (id, public_key, wrapped_private_key)
                        VALUES (1, :public_key, :wrapped_private_key)
                        ON CONFLICT (id) DO NOTHING;""")
                .single(call().bind("public_key", publicKey).bind("wrapped_private_key", wrappedPrivateKey))
                .insert()
                .changed();
    }
}
