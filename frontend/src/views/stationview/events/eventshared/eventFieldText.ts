/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {isMemberFieldType} from './eventFieldConfig'

/**
 * The people a member field names.
 *
 * <p>A field holding one person stores the bare member id, a field holding several stores a JSON
 * array of them, and both arrive as text. Anything that parses as neither is read as a single id,
 * which is what the field editor writes for one person.
 */
export function eventFieldMemberIds(value?: string | null): number[] {
    if (!value) return []
    try {
        const parsed = JSON.parse(value)
        if (Array.isArray(parsed)) return parsed.map(Number).filter(id => Number.isFinite(id))
        if (typeof parsed === 'number' && Number.isFinite(parsed)) return [parsed]
    } catch {
        /* not JSON, so it is a bare id below */
    }
    const single = Number(value)
    return Number.isFinite(single) ? [single] : []
}

/** What a member field's stored ids are called, given the names the caller could resolve. */
function memberNames(value: string | undefined, names: Map<number, string>): string {
    return eventFieldMemberIds(value)
        .map(id => names.get(id) ?? `#${id}`)
        .join(', ')
}

/**
 * One custom field's stored value as a reader would see it.
 *
 * <p>A field's value is text whatever its type says, so a yes/no field holds the word `true` and a
 * field naming a person holds a number. Only the first of those is resolved anywhere in the
 * appointment screens today, which is why anything reading a value out of its own screen has to
 * come through here rather than printing what is stored.
 *
 * @param field  the field, with its declared type and its stored value
 * @param names  the station's members by id, used for the field types that name people
 * @param labels what yes and no are called in the reader's language
 */
export function eventFieldText(
    field: {fieldType?: string; value?: string},
    names: Map<number, string>,
    labels: {yes: string; no: string},
): string {
    const value = field.value?.trim() ?? ''
    if (!value) return ''
    if (field.fieldType === 'BOOLEAN') return value === 'true' ? labels.yes : labels.no
    if (field.fieldType && isMemberFieldType(field.fieldType)) return memberNames(value, names)
    return value
}
