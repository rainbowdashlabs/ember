/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.time.Instant;

/**
 * An RFC 3161 timestamp a service gave for a hash, already checked to chain to the root pinned for it.
 *
 * <p>The token is handed over as it is, without a copy.
 *
 * @param token   the timestamp token, a DER encoded CMS signed data
 * @param time    the time the token states
 * @param service the address of the service that gave it
 */
public record ObtainedTimestamp(byte[] token, Instant time, String service) {}
