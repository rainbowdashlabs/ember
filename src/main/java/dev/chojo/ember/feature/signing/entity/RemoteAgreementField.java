/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One signature field of a document signed at a member's home installation, as the home reports it with a
 * sealed copy. Who signed is on the copy's record page; the field only says where it stands.
 *
 * @param name      the name of the field in the document: participant, guardian1 or anyGuardian
 * @param state     where it stands at home
 * @param settledAt when it stopped being open, or null while it is
 */
public record RemoteAgreementField(
        String name, FieldState state, @Nullable Instant settledAt) {}
