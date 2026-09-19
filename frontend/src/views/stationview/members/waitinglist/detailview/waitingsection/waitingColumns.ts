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
import {ColumnTypes, columnTypeOf, toCellValue, type CellValue, type TableColumn} from '@/components/table/tableColumn'

type Row = WaitingListEntryWithScore

export const SCORE_KEY = 'score'
export const BIRTH_DATE_KEY = 'birthDate'
const FIELD_KEY_PREFIX = 'field-'

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

const answersByEntry = new WeakMap<Row, Map<number, CellValue>>()

/** One entry's answers by question, each read into a cell once however often the table asks. */
function answersOf(row: Row): Map<number, CellValue> {
    let answers = answersByEntry.get(row)
    if (!answers) {
        answers = new Map(row.values.map(value => [value.fieldId, toCellValue(value.value)]))
        answersByEntry.set(row, answers)
    }
    return answers
}

function fieldColumn(field: WaitingListField, visibleFieldIds: ReadonlySet<number>): TableColumn<Row> {
    return {
        key: `${FIELD_KEY_PREFIX}${field.id}`,
        label: field.name,
        type: columnTypeOf(field.fieldType),
        value: row => answersOf(row).get(field.id) ?? null,
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
