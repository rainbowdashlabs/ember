/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Hands an encrypted push to a browser's push service. */
public interface PushTransport {

    /**
     * What the push service answered.
     *
     * @param status     the HTTP status, or 0 when the address may not be reached at all
     * @param retryAfter how long it asked to wait, where it gave one
     */
    record Answer(int status, Duration retryAfter) {
        /** @return whether the push service took the push */
        public boolean accepted() {
            return status >= 200 && status < 300;
        }

        /** @return whether the push service does not know the browser any more */
        public boolean gone() {
            return status == 0 || status == 404 || status == 410;
        }

        /** @return whether trying again later may work */
        public boolean worthRetrying() {
            return status == 429 || status >= 500;
        }
    }

    /**
     * Posts a push.
     *
     * @param endpoint the subscription's address
     * @param headers  the headers Web Push needs
     * @param body     the encrypted body
     * @return the answer
     * @throws IOException when the push service could not be reached
     */
    Answer post(URI endpoint, Map<String, String> headers, byte[] body) throws IOException, InterruptedException;
}
