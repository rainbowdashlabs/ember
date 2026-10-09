/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FillInResponse, SignerEntryDraft} from '@/api/generated/schema'

/** What the signer typed so far, by the name of the field. */
export type FillInValues = Record<string, string>

/**
 * What the act binds of the typed values: every field filled in, exactly as typed, in the document's
 * order. A field left empty, or holding spaces only, is not sent, which is how an optional field stays
 * empty.
 *
 * @param fields the fields the document asks the signer to fill in
 * @param values what the signer typed
 */
export function fillInEntries(fields: readonly FillInResponse[], values: FillInValues): SignerEntryDraft[] {
    return fields
        .filter(field => (values[field.name] ?? '').trim() !== '')
        .map(field => ({field: field.name, value: values[field.name]}))
}

/**
 * Whether the act can start: every required field is filled in and no value is longer than its field.
 *
 * @param fields the fields the document asks the signer to fill in
 * @param values what the signer typed
 */
export function fillInsComplete(fields: readonly FillInResponse[], values: FillInValues): boolean {
    return fields.every(field => {
        const value = values[field.name] ?? ''
        if (value.length > field.maxLength) return false
        return !field.required || value.trim() !== ''
    })
}
