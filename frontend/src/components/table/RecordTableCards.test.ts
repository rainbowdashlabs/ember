/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {defineComponent, h, ref} from 'vue'
import {beforeEach, describe, expect, it} from 'vitest'
import {ColumnTypes, type TableColumn} from './tableColumn'
import {emptyTableState, useDataTable, type DataTableApi} from '@/composables/useDataTable'
import RecordTableCards from './RecordTableCards.vue'

interface Piece {
    id: number
    name: string
    code: string
}

const PIECES: Piece[] = [{id: 1, name: 'Bundhose', code: '000068'}]

const COLUMNS: TableColumn<Piece>[] = [
    {key: 'name', label: 'Name', type: ColumnTypes.TEXT, value: p => p.name, pinned: true},
    {key: 'code', label: 'Kennung', type: ColumnTypes.TEXT, value: p => p.code},
]

function tableOf(): DataTableApi<Piece> {
    let api: DataTableApi<Piece> | null = null
    mount(defineComponent({
        setup() {
            api = useDataTable<Piece>({id: 'pieces', rows: PIECES, columns: COLUMNS, rowKey: p => p.id, state: ref(emptyTableState())})
            return () => null
        },
    }))
    return api as unknown as DataTableApi<Piece>
}

function cardsOf(menuActions: boolean) {
    const table = tableOf()
    return mount(RecordTableCards<Piece>, {
        props: {table, rows: table.rows, testId: 'cards', rowTestId: 'card', clickable: false, controls: false, menuActions},
        slots: {actions: () => h('button', {'data-testid': 'menu'}, 'Aktionen')},
    })
}

describe('RecordTableCards', () => {
    beforeEach(() => {
        localStorage.clear()
        localStorage.setItem('storage_consent', 'accepted')
    })

    it('puts the actions at the foot of the card under a rule of their own', () => {
        const card = cardsOf(false).get('[data-testid="card"]')

        const foot = card.get('[data-testid="menu"]').element.parentElement
        expect(foot?.className).toContain('border-t')
    })

    it('keeps a menu beside the heading and spends no line at the foot on it', () => {
        const card = cardsOf(true).get('[data-testid="card"]')

        const menu = card.get('[data-testid="menu"]')
        expect(menu.element.parentElement?.className).not.toContain('border-t')
        expect(menu.element.parentElement?.parentElement?.textContent).toContain('Bundhose')
        expect(card.findAll('[data-testid="menu"]')).toHaveLength(1)
        expect(card.find('.border-t').exists()).toBe(false)
    })
})
