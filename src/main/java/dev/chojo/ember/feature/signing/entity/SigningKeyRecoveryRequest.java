/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;

/**
 * An instance administrator's confirmation to give up the signing keys that no longer open.
 *
 * @param serialNumbers the serial numbers of exactly the keys they were shown as no longer opening; a
 *                      recovery runs only while that is still the whole list
 */
public record SigningKeyRecoveryRequest(List<String> serialNumbers) {}
