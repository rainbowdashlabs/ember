/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.List;

/**
 * What a check of an uploaded PDF found: each signature with its timestamps, each timestamp on the
 * document itself, and whether this installation holds exactly this file. A PDF without any signature
 * has two empty lists.
 *
 * @param document           whether this installation holds this very file, and if so when it was sealed
 * @param signatures         every signature, in the order the document holds them
 * @param documentTimestamps every timestamp on the document itself, in the order the document holds them
 */
public record SealVerification(
        HeldCopy document, List<SealCheck> signatures, List<DocumentTimestampCheck> documentTimestamps) {

    /** Copies the lists, so the result cannot change after the fact. */
    public SealVerification {
        signatures = List.copyOf(signatures);
        documentTimestamps = List.copyOf(documentTimestamps);
    }
}
