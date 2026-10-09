/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * A value the signer typed into a field of their own at signing, such as a phone number or an emergency
 * contact. It is the signer's own statement, not checked against anything, and it is part of what the
 * signature covers.
 *
 * @param field the name of the field in the document
 * @param value what the signer typed, exactly as typed
 */
public record SignerEntry(String field, String value) {}
