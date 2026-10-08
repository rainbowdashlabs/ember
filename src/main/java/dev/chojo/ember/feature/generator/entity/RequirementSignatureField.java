/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * One signature field of a participant's copy of a document an appointment asks for.
 *
 * @param id         the field, which the signing screen opens
 * @param name       the name of the field in the document, which says who signs it: participant,
 *                   guardian1 and further guardians by place, anyGuardian or issuer
 * @param signerName the official name of whoever is asked to sign it, or null where nobody in particular is
 * @param state      where the field stands
 * @param yours      whether the reader can sign it now, for themselves or for a member in their care
 */
public record RequirementSignatureField(
        int id, String name, @Nullable String signerName, RequirementSignatureState state, boolean yours) {}
