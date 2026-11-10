/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {StepDocumentState} from '@/api/generated/schema'

/** What a document step's row shows of its document, and the ways on from there the reader has. */
export interface StepDocumentView {
    /** The name of the document the step asks to sign, where its template is still there. */
    name?: string
    /** Where the document it made stands with its signatures, or nothing where it made none yet. */
    state: StepDocumentState | null
    /** Where the reader opens the document, or nothing where they may not. */
    href: string | null
    /** Where the reader has it signed in front of them, or signs it themselves, or nothing. */
    signHref: string | null
    /** What the sign link says: signing in front of a witness, or signing oneself. */
    signLabel: string
    /** Whether the reader may ask for the document again. */
    canAskAgain: boolean
}
