/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {WaitingListFieldTypes, type WaitingListEntryWithScore, type WaitingListField} from '@/api/waitingList'
import {BIRTH_DATE_KEY, fieldIdOfColumn, waitingColumns} from './waitingColumns'

function field(id: number, fieldType: WaitingListField['fieldType']): WaitingListField {
    return {id, listId: 1, name: `field ${id}`, fieldType, config: {}, position: id, required: false, isPublic: false}
}

function entry(values: Record<number, unknown>): WaitingListEntryWithScore {
    return {
        entry: {firstname: 'Ada', lastname: 'Lovelace', status: 'WAITING', createdAt: '2026-01-01'},
        values: Object.entries(values).map(([fieldId, value]) => ({entryId: 1, fieldId: Number(fieldId), value})),
        score: 3,
        guardians: [],
        belowJoinAge: false,
    } as unknown as WaitingListEntryWithScore
}

const t = (key: string) => key

describe('waitingColumns', () => {
    const fields = [field(1, WaitingListFieldTypes.BIRTH_DATE), field(2, WaitingListFieldTypes.BOOLEAN), field(3, WaitingListFieldTypes.NUMBER)]
    const columns = waitingColumns({t, fields, visibleFieldIds: new Set([2])})

    it('gives the date of birth a pinned column of its own and offers only the other questions', () => {
        const birthDate = columns.find(column => column.key === BIRTH_DATE_KEY)
        expect(birthDate?.pinned).toBe(true)
        expect(columns.filter(column => !column.pinned).map(column => column.key)).toEqual(['field-2', 'field-3'])
    })

    it('shows the questions the list keeps as shown, and only those', () => {
        expect(columns.find(column => column.key === 'field-2')?.defaultVisible).toBe(true)
        expect(columns.find(column => column.key === 'field-3')?.defaultVisible).toBe(false)
    })

    it('types an answer the way its column sorts it', () => {
        const row = entry({2: 'true', 3: '12'})
        expect(columns.find(column => column.key === 'field-2')?.value(row)).toBe(true)
        expect(columns.find(column => column.key === 'field-3')?.value(row)).toBe(12)
        expect(columns.find(column => column.key === BIRTH_DATE_KEY)?.value(row)).toBeNull()
    })

    it('reads the question back from a column key', () => {
        expect(fieldIdOfColumn('field-7')).toBe(7)
        expect(fieldIdOfColumn('score')).toBeNull()
    })
})
