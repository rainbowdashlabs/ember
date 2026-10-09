/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * The document a signer reads before the act, exactly as the request froze it.
 *
 * @param fileName the name it was filed under
 * @param pdf      its bytes, whose SHA-256 is the request's content hash
 */
public record DocumentToSign(String fileName, byte[] pdf) {}
