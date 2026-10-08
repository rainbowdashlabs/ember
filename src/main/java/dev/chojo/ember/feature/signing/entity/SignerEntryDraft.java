/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * A value the signer typed into a field of their own, as the browser sent it and before it is checked.
 *
 * @param field the name of the field in the document
 * @param value what the signer typed
 */
public record SignerEntryDraft(
        @Nullable String field, @Nullable String value) {}
