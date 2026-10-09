/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * A completed signing act about to be recorded, with the signature picture it leaves in its field.
 *
 * @param signing what the provider handed back for the field
 * @param picture the picture the act leaves in its field, or null for an act that leaves none
 */
public record SignedAct(CompletedSigning signing, @Nullable ActPicture picture) {}
