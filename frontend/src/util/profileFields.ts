/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Ref} from 'vue'

/**
 * Reads a profile field value from a {@link Map}-backed {@link Ref}, returning
 * the empty string when the field is not yet set.
 */
export function getFieldValue(values: Ref<Map<number, string>>, fieldId: number): string {
    return values.value.get(fieldId) ?? ''
}

/**
 * Writes a profile field value into a {@link Map}-backed {@link Ref}.
 *
 * Replaces the underlying {@link Map} instance so Vue treats the assignment
 * as a reactive change.
 */
export function setFieldValue(values: Ref<Map<number, string>>, fieldId: number, value: string): void {
    const next = new Map(values.value)
    next.set(fieldId, value)
    values.value = next
}

/**
 * Shape of a single profile-field value entry as returned by the backend.
 */
export interface ProfileFieldValueEntry {
    fieldId: number
    value: string | null | undefined
}

/**
 * Decodes a list of profile-field value entries into a {@link Map} keyed by
 * field id.
 *
 * Values arrive JSON-encoded; decoding falls back to the raw string when
 * parsing fails so legacy or hand-edited rows still load.
 */
export function decodeProfileValues(entries: ReadonlyArray<ProfileFieldValueEntry>): Map<number, string> {
    const map = new Map<number, string>()
    for (const entry of entries) {
        let val: unknown = entry.value ?? ''
        try {
            val = JSON.parse(val as string)
        } catch {
            void 0
        }
        map.set(entry.fieldId, typeof val === 'string' ? val : String(val))
    }
    return map
}
