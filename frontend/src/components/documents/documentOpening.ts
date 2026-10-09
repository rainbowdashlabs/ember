/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** A document a link asks to open, and the sealed version whose record the reader came for, if any. */
export interface DocumentOpening {
    documentId: number
    record: number | null
}

function positive(value: unknown): number | null {
    const number = typeof value === 'string' ? Number(value) : NaN
    return Number.isInteger(number) && number > 0 ? number : null
}

/**
 * What the address asks to open, as a signer's copy mail writes it: `?document=<id>` and, for its record,
 * `&record=<version>`.
 *
 * @param query the route's query
 * @returns the document to open, or null where the address names none
 */
export function documentOpeningOf(query: Readonly<Record<string, unknown>>): DocumentOpening | null {
    const documentId = positive(query.document)
    return documentId === null ? null : {documentId, record: positive(query.record)}
}
