/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import java.time.Instant;

/**
 * A passkey code issued for somebody who signs in without an address of their own, held up in the
 * room by whoever looks after them.
 *
 * @param code      the code to type in
 * @param qrPng     base64 PNG of a QR code carrying the same grant
 * @param expiresAt when the code stops working
 */
public record PasskeyCodeResponse(String code, String qrPng, Instant expiresAt) {}
