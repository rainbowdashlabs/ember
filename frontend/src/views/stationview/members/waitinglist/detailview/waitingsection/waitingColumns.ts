/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    WaitingListEntryStatus,
    WaitingListFieldTypes,
    type WaitingListEntryWithScore,
    type WaitingListField,
} from '@/api/waitingList'
import {ColumnTypes, type CellValue, type ColumnType, type TableColumn} from '@/components/table/tableColumn'

type Row = WaitingListEntryWithScore

export const SCORE_KEY = 'score'
export const BIRTH_DATE_KEY = 'birthDate'
const FIELD_KEY_PREFIX = 'field-'

const FIELD_TYPE_COLUMNS: Record<string, ColumnType> = {
    [WaitingListFieldTypes.NUMBER]: ColumnTypes.NUMBER,
    [WaitingListFieldTypes.DATE]: ColumnTypes.DATE,
    [WaitingListFieldTypes.BIRTH_DATE]: ColumnTypes.BIRTH_DATE,
    [WaitingListFieldTypes.BOOLEAN]: ColumnTypes.BOOLEAN,
    [WaitingListFieldTypes.ENUM]: ColumnTypes.ENUM,
}

/** What the waiting list's columns need to know beyond the entries themselves. */
export interface WaitingColumnSources {
    t: (key: string) => string
    fields: readonly WaitingListField[]
    /** The questions the list shows as columns, which the list keeps for everybody who reads it. */
    visibleFieldIds: ReadonlySet<number>
}

/** The id of the question a column stands for, or null for a column of the entry itself. */
export function fieldIdOfColumn(key: string | number): number | null {
    const text = String(key)
    return text.startsWith(FIELD_KEY_PREFIX) ? Number(text.slice(FIELD_KEY_PREFIX.length)) : null
}

/** One entry's answer to one question, typed the way its column sorts and filters it. */
function answerOf(row: Row, field: WaitingListField): CellValue {
    const raw = row.values.find(value => value.fieldId === field.id)?.value
    if (raw === null || raw === undefined || raw === '') return null
    switch (field.fieldType) {
        case WaitingListFieldTypes.BOOLEAN: return raw === true || raw === 'true'
        case WaitingListFieldTypes.NUMBER: return Number(raw)
        default: return String(raw)
    }
}

function fieldColumn(field: WaitingListField, visibleFieldIds: ReadonlySet<number>): TableColumn<Row> {
    return {
        key: `${FIELD_KEY_PREFIX}${field.id}`,
        label: field.name,
        type: FIELD_TYPE_COLUMNS[field.fieldType] ?? ColumnTypes.TEXT,
        value: row => answerOf(row, field),
        options: field.config.options?.map(option => ({value: option, label: option})),
        defaultVisible: visibleFieldIds.has(field.id),
    }
}

function birthDateColumn(field: WaitingListField): TableColumn<Row> {
    return {...fieldColumn(field, new Set()), key: BIRTH_DATE_KEY, pinned: true}
}

/**
 * The columns of the people waiting: their names, since when they wait, the date of birth where the
 * list asks for one, the questions the list shows, their status and their score.
 *
 * <p>Only the questions are offered to be shown or hidden. The date of birth has a column of its own
 * because the list works out ages from it, so it is never offered a second time.
 */
export function waitingColumns(sources: WaitingColumnSources): TableColumn<Row>[] {
    const {t, fields, visibleFieldIds} = sources
    const birthDate = fields.find(field => field.fieldType === WaitingListFieldTypes.BIRTH_DATE)
    const statuses = [WaitingListEntryStatus.WAITING, WaitingListEntryStatus.INVITED]
    return [
        {key: 'firstname', label: t('waitingList.firstname'), type: ColumnTypes.TEXT, value: row => row.entry.firstname, pinned: true},
        {key: 'lastname', label: t('waitingList.lastname'), type: ColumnTypes.TEXT, value: row => row.entry.lastname, pinned: true},
        {key: 'createdAt', label: t('waitingList.createdAt'), type: ColumnTypes.DATE, value: row => row.entry.createdAt, pinned: true},
        ...(birthDate ? [birthDateColumn(birthDate)] : []),
        ...fields.filter(field => field !== birthDate).map(field => fieldColumn(field, visibleFieldIds)),
        {
            key: 'status',
            label: t('waitingList.status'),
            type: ColumnTypes.ENUM,
            value: row => row.entry.status,
            options: statuses.map(status => ({value: status, label: t(`waitingList.status_${status}`)})),
            pinned: true,
        },
        {key: SCORE_KEY, label: t('waitingList.score'), type: ColumnTypes.NUMBER, value: row => row.score, pinned: true, align: 'right'},
    ]
}
