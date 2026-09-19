/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent, nextTick, ref} from 'vue'
import {beforeEach, describe, expect, it} from 'vitest'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import {emptyTableState, useDataTable, type DataTableApi} from './useDataTable'

interface Person {
    id: number
    name: string
    born: string | null
    shoe: number | null
    role: string
    active: boolean
    groups: string[]
}

const PEOPLE: Person[] = [
    {id: 1, name: 'Anna', born: '2015-12-15', shoe: 38, role: 'LEAD', active: true, groups: ['Jugend']},
    {id: 2, name: 'Ben', born: '2016-01-02', shoe: 42, role: 'MEMBER', active: false, groups: ['Jugend', 'Chor']},
    {id: 3, name: 'Cara', born: null, shoe: null, role: 'MEMBER', active: true, groups: []},
]

const COLUMNS: TableColumn<Person>[] = [
    {key: 'name', label: 'Name', type: ColumnTypes.TEXT, value: p => p.name, pinned: true},
    {key: 'born', label: 'Geboren', type: ColumnTypes.DATE, value: p => p.born},
    {key: 'shoe', label: 'Schuhgröße', type: ColumnTypes.NUMBER, value: p => p.shoe},
    {
        key: 'role', label: 'Rolle', type: ColumnTypes.ENUM, value: p => p.role,
        options: [{value: 'LEAD', label: 'Leitung'}, {value: 'MEMBER', label: 'Mitglied'}],
    },
    {key: 'active', label: 'Aktiv', type: ColumnTypes.BOOLEAN, value: p => p.active},
    {key: 'groups', label: 'Gruppen', type: ColumnTypes.TEXT, value: p => p.groups, defaultVisible: false},
]

function tableOf(id = 'people', state = ref(emptyTableState())): DataTableApi<Person> {
    let api: DataTableApi<Person> | null = null
    mount(defineComponent({
        setup() {
            api = useDataTable<Person>({id, rows: PEOPLE, columns: COLUMNS, rowKey: p => p.id, state})
            return () => null
        },
    }))
    return api as unknown as DataTableApi<Person>
}

function names(table: DataTableApi<Person>): string[] {
    return table.rows.map(row => row.name)
}

describe('useDataTable', () => {
    beforeEach(() => {
        localStorage.clear()
        localStorage.setItem('storage_consent', 'accepted')
    })

    it('sorts dates by day rather than by the words shown', () => {
        const table = tableOf()
        table.toggleSort('born')

        expect(names(table)).toEqual(['Anna', 'Ben', 'Cara'])
    })

    it('keeps empty cells last whichever way a column is turned', () => {
        const table = tableOf()
        table.toggleSort('shoe')
        table.toggleSort('shoe')

        expect(names(table)).toEqual(['Ben', 'Anna', 'Cara'])
    })

    it('sorts options in their own order', () => {
        const table = tableOf()
        table.toggleSort('role')
        table.toggleSort('role')

        expect(names(table)[2]).toBe('Anna')
    })

    it('offers the labels of an enum and matches its stored values', () => {
        const table = tableOf()
        table.openFilter('role')

        expect(table.filterDialog?.choices).toEqual([
            {value: 'LEAD', label: 'Leitung'},
            {value: 'MEMBER', label: 'Mitglied'},
        ])
        table.setFilter('role', new Set(['LEAD']), false)
        expect(names(table)).toEqual(['Anna'])
    })

    it('opens the filter a column type calls for', () => {
        const table = tableOf()

        table.openFilter('shoe')
        expect(table.filterDialog?.kind).toBe('number')
        table.openFilter('born')
        expect(table.filterDialog?.kind).toBe('date')
        table.openFilter('active')
        expect(table.filterDialog?.choices.map(choice => choice.label)).toEqual(['Ja', 'Nein'])
        table.closeFilter()
        expect(table.filterDialog).toBeNull()
    })

    it('opens on the sort it is given', () => {
        let api: DataTableApi<Person> | null = null
        mount(defineComponent({
            setup() {
                api = useDataTable<Person>({id: 'sorted', rows: PEOPLE, columns: COLUMNS, rowKey: p => p.id, sort: {key: 'shoe', direction: 'desc'}})
                return () => null
            },
        }))

        expect(names(api as unknown as DataTableApi<Person>)).toEqual(['Ben', 'Anna', 'Cara'])
    })

    it('sorts before it searches, so narrowing keeps the order', () => {
        const table = tableOf()
        table.toggleSort('shoe')
        table.toggleSort('shoe')
        table.search = 'a'

        expect(names(table)).toEqual(['Anna', 'Cara'])
    })

    it('narrows a number column to a range', () => {
        const table = tableOf()
        table.setFilter('shoe', new Set(['min:40']), false)

        expect(names(table)).toEqual(['Ben'])
    })

    it('keeps only the empty cells where only empties are asked for', () => {
        const table = tableOf()
        table.setFilter('born', new Set(), true)

        expect(names(table)).toEqual(['Cara'])
    })

    it('drops the filter of a column taken out of view', () => {
        const table = tableOf()
        table.setFilter('shoe', new Set(['min:40']), false)
        table.setColumnsVisible(['shoe'], false)

        expect(names(table)).toEqual(['Anna', 'Ben', 'Cara'])
    })

    it('matches any of the things a cell lists', () => {
        const table = tableOf()
        table.setColumnsVisible(['groups'], true)
        table.setFilter('groups', new Set(['Chor']), false)

        expect(names(table)).toEqual(['Ben'])
    })

    it('never offers a pinned column in the column list', () => {
        const table = tableOf()

        expect(table.pickerOptions.map(option => option.key)).not.toContain('name')
    })

    it('remembers chosen columns for the next visit', () => {
        tableOf('remembered').setColumnsVisible(['groups'], true)

        expect(tableOf('remembered').visibleColumns.map(column => column.key)).toContain('groups')
        expect(tableOf('elsewhere').visibleColumns.map(column => column.key)).not.toContain('groups')
    })

    it('keeps every table in the one stored value', () => {
        tableOf('first').setColumnsVisible(['groups'], true)
        tableOf('second').setColumnsVisible(['shoe'], false)

        const keys = Object.keys(localStorage).filter(key => key !== 'storage_consent')
        expect(keys).toEqual(['table_columns'])
    })

    it('remembers nothing where comfort storage was declined', () => {
        localStorage.setItem('storage_scopes', 'FUNCTIONAL')
        tableOf('declined').setColumnsVisible(['groups'], true)

        expect(localStorage.getItem('table_columns')).toBeNull()
    })

    it('follows the state it is handed, such as another tab', async () => {
        const first = emptyTableState()
        const second = emptyTableState()
        second.search = 'ben'
        const state = ref(first)
        const table = tableOf('tabs', state)

        state.value = second
        await nextTick()

        expect(names(table)).toEqual(['Ben'])
    })
})
