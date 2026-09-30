/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import java.nio.charset.StandardCharsets;

/**
 * A signature over the request body exactly as transmitted, nothing else.
 *
 * <p>Used by discovery and beacon deliveries, whose bodies carry their own issue time, nonce and
 * audience, and by the federation handshake, which precedes any partnership and therefore any
 * recipient to bind.
 *
 * @param body the body as sent, {@code null} read as empty
 */
public record RawBodyEnvelope(String body) implements SignedEnvelope {

    @Override
    public byte[] bytes() {
        return (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
    }
}
