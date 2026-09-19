/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {sortValueOf} from '@/components/table/columnFilter'
import {compareSortValues} from '@/composables/useSortable'
import {ColumnTypes} from '@/components/table/tableColumn'
import type {MemberTableHeader} from '@/api/memberTable'
import {readDrawnTable, tableColumnOf, type DrawnRow} from './registrationTableColumns'

const YES_NO = {yes: 'Ja', no: 'Nein'}

function rowWith(header: MemberTableHeader, cell: string): DrawnRow<null> {
    const [drawn] = readDrawnTable({columns: [header], rows: [{memberId: 1, values: [cell]}]})
    return {...drawn!, extra: null}
}

describe('registrationTableColumns', () => {
    const joined: MemberTableHeader = {label: 'Eintritt', kind: 'BUILTIN', key: 'joinDate', fieldId: null, type: 'DATE'}

    it('sorts drawn days by the day rather than by how they read', () => {
        const column = tableColumnOf<null>(joined)

        const earlier = rowWith(joined, '15.12.2025')
        const later = rowWith(joined, '02.01.2026')

        expect(column.type).toBe(ColumnTypes.DATE)
        expect(compareSortValues(sortValueOf(column, earlier, YES_NO), sortValueOf(column, later, YES_NO))).toBeLessThan(0)
    })

    it('reads an appointment answer given as an ISO day the same way', () => {
        const question: MemberTableHeader = {label: 'Anreise', kind: 'REGISTRATION_FIELD', key: null, fieldId: 3, type: 'DATE'}

        expect(tableColumnOf<null>(question).value(rowWith(question, '2026-01-02'))).toBe('2026-01-02')
    })

    it('reads the groups a person is in as a list to filter by', () => {
        const groups: MemberTableHeader = {label: 'Gruppen', kind: 'BUILTIN', key: 'groups', fieldId: null, type: 'TEXT'}

        expect(tableColumnOf<null>(groups).value(rowWith(groups, 'Jugend, Chor'))).toEqual(['Jugend', 'Chor'])
    })
})
