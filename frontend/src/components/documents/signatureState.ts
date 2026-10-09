/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {RequestState, type SignatureSummary} from '@/api/generated/schema'

/**
 * How the signatures on a document read in a list: signed, partly signed, open, withdrawn, replaced by a
 * corrected document, or never asked for.
 */
export const SignatureDisplay = {
    SIGNED: 'SIGNED',
    PARTLY_SIGNED: 'PARTLY_SIGNED',
    OPEN: 'OPEN',
    WITHDRAWN: 'WITHDRAWN',
    REPLACED: 'REPLACED',
    NOT_ASKED: 'NOT_ASKED',
} as const

export type SignatureDisplay = typeof SignatureDisplay[keyof typeof SignatureDisplay]

/** The order the states sort in, from the furthest along to the ones nobody waits for. */
export const SIGNATURE_DISPLAY_ORDER: readonly SignatureDisplay[] = [
    SignatureDisplay.SIGNED,
    SignatureDisplay.PARTLY_SIGNED,
    SignatureDisplay.OPEN,
    SignatureDisplay.WITHDRAWN,
    SignatureDisplay.REPLACED,
    SignatureDisplay.NOT_ASKED,
]

/**
 * How the signatures on a document read, from the summary of its newest request.
 *
 * @param summary the summary, or null where nobody was asked to sign the document
 */
export function signatureDisplayOf(summary: SignatureSummary | null | undefined): SignatureDisplay {
    if (!summary) return SignatureDisplay.NOT_ASKED
    switch (summary.state) {
        case RequestState.COMPLETE:
            return SignatureDisplay.SIGNED
        case RequestState.WITHDRAWN:
        case RequestState.REVOKED:
            return SignatureDisplay.WITHDRAWN
        case RequestState.SUPERSEDED:
            return SignatureDisplay.REPLACED
        default:
            return summary.signed > 0 ? SignatureDisplay.PARTLY_SIGNED : SignatureDisplay.OPEN
    }
}

/**
 * Whether a request still waits for a field nobody can sign, which only a manager settles: on paper, by
 * waiving it or by withdrawing it.
 */
export function waitsForNobody(summary: SignatureSummary | null | undefined): boolean {
    return summary?.state === RequestState.OPEN && summary.nobodyCanSign > 0
}
