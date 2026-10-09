/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One field a signer chose to sign together with others, and what they typed into the fields of their own
 * it asks them to fill in.
 *
 * @param fieldId the signature field
 * @param entries the values typed for it, or null for none
 */
public record BatchChoice(int fieldId, @Nullable List<SignerEntryDraft> entries) {}
