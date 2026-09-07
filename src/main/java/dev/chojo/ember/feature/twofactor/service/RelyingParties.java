/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.yubico.webauthn.RelyingParty;

/**
 * The two views of the one relying party. Both share the rpId, the origin set, the user handles
 * and the tables; what differs is which credentials each answers with when the library asks for
 * an account's credential ids.
 *
 * @param passkey the full view: every credential the account has. Registration ceremonies run
 *         here so the exclude list covers everything, and the passkey ceremonies run here too.
 * @param secondFactor the narrow view: only credentials flagged as second factors, so an
 *         assertion after a password never asks for a passkey.
 * @param localhostFallback whether the rpId could not be derived and fell back to
 *         {@code localhost}. A passkey bound to the wrong effective domain is worse than no
 *         passkey, so the effective passkey mode is held at OFF while this is true.
 */
public record RelyingParties(RelyingParty passkey, RelyingParty secondFactor, boolean localhostFallback) {}
