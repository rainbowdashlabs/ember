/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;

/**
 * Whether the installation's signing keys still open under the at-rest secret, for its administrators.
 *
 * @param locked     the keys in use that no longer open, authorities first; empty while every key opens
 * @param openKeys   how many keys in use open
 * @param recoveries the earlier recoveries, newest first
 */
public record SigningKeyStatus(List<LockedSigningKey> locked, int openKeys, List<SigningKeyRecoveryEntry> recoveries) {}
