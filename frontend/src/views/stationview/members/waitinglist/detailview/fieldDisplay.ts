/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {formatDate} from '@/util/format'
import {WaitingListFieldTypes, type WaitingListField} from '@/api/waitingList'

/** The types whose answer is a date and therefore has to be written the way a date is written. */
const DATE_TYPES: readonly string[] = [WaitingListFieldTypes.DATE, WaitingListFieldTypes.BIRTH_DATE]

/**
 * How the answer to a field is shown. An answer is kept the way it is stored and would otherwise be
 * put on the page exactly like that: a date as the machine writes one, a yes as the word `true`.
 *
 * @param translate the caller's own translator, since a plain module has none of its own
 */
export function displayFieldValue(
    field: WaitingListField,
    raw: string | null | undefined,
    translate: (key: string) => string,
): string {
    if (raw == null || raw === '') return ''
    if (DATE_TYPES.includes(field.fieldType)) return formatDate(raw)
    if (field.fieldType === WaitingListFieldTypes.BOOLEAN) {
        return raw === 'true' ? translate('common.yes') : translate('common.no')
    }
    return raw
}
