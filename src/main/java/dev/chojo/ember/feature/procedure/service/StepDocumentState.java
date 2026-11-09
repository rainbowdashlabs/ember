/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import org.jspecify.annotations.Nullable;

/**
 * Where the document a step made stands with its signatures.
 *
 * @param itemId      the step
 * @param documentId  the member document it was filed as, or null where it is no longer kept
 * @param progress    where its signatures stand
 * @param settled     how many of its fields were signed, confirmed on paper, waived or withdrawn
 * @param fields      how many fields it asks for
 * @param openFieldId a field still waiting for a signature, to sign it in front of a witness, or null
 *                    where none waits
 */
public record StepDocumentState(
        int itemId,
        @Nullable Integer documentId,
        Progress progress,
        int settled,
        int fields,
        @Nullable Integer openFieldId) {

    /** Where the signatures of a step's document stand. */
    public enum Progress {
        /** The document was made, but its signatures were never asked for. */
        NOT_ASKED,
        /** Its signatures are asked for, and some may already be given. */
        WAITING,
        /** It stands as agreed: signed, confirmed on paper or waived. */
        AGREED,
        /** Every field of it was withdrawn before anybody agreed. */
        NOT_AGREED,
        /** It was agreed, and the agreement was withdrawn afterwards. */
        WITHDRAWN
    }
}
