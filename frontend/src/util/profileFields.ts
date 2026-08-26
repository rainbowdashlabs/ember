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
    value?: string | null
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

/** Who asked a profile question: the station itself, or the cluster above it. */
export type FieldOrigin = 'STATION' | 'CLUSTER'

/**
 * A field as the member profile sees it, whoever asked it.
 *
 * The profile is the one screen that shows both, so it is the one place that has to tell them apart. A
 * station numbers its own fields and a cluster numbers its own, in separate tables, so the id alone is
 * not a name: {@link profileKey} is.
 */
export interface MergedProfileField {
    id: number
    /** Absent on a cluster's question, which belongs to no one station. */
    stationId?: string
    name?: string
    fieldType?: string
    config?: Record<string, unknown>
    position: number
    scope?: string
    origin: FieldOrigin
    /** Whether the people at the station may read the answer but not write it. Only a cluster field can be. */
    readonlyAtStation: boolean
}

/** The key an answer is held under on the profile, which is the pair and not the id. */
export function profileKey(fieldId: number, origin: FieldOrigin): string {
    return `${origin}:${fieldId}`
}

/**
 * Decodes profile answers of both origins into a map keyed by {@link profileKey}.
 *
 * The plain {@link decodeProfileValues} keys by id alone, which is right for every list of fields that
 * has one origin and wrong for the profile, where two questions can carry the same number.
 */
export function decodeMergedValues(
    entries: ReadonlyArray<ProfileFieldValueEntry & {origin?: FieldOrigin}>,
): Map<string, string> {
    const map = new Map<string, string>()
    for (const entry of entries) {
        let val: unknown = entry.value ?? ''
        try {
            val = JSON.parse(val as string)
        } catch {
            void 0
        }
        map.set(profileKey(entry.fieldId, entry.origin ?? 'STATION'),
            typeof val === 'string' ? val : String(val))
    }
    return map
}
