/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * One signing authority as a station states it to a partner.
 *
 * @param certificate    the authority's certificate, DER encoded
 * @param active         whether it issues new station certificates, false once it was retired or given up
 * @param revocationList its current revocation list, DER encoded; null when none could be had, as for an
 *                       authority whose key no longer opens and that never stored one
 */
public record StatedAuthority(byte[] certificate, boolean active, byte @Nullable [] revocationList) {}
