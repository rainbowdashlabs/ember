/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import org.jspecify.annotations.Nullable;

/**
 * How the signer confirms a started act, as their browser sent it. The code or password is checked once
 * and never kept.
 *
 * @param proof          the kind of proof the signer gives
 * @param credentialJson the passkey or security key answer as the browser returned it, for those two
 * @param secret         the authenticator app code or the password, for those two
 */
public record SigningAnswer(
        StepUpProof proof,
        @Nullable String credentialJson,
        @Nullable String secret) {}
