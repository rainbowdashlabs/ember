/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import {useExport} from '@/composables/useExport'
import {useExchangeTable} from './useExchangeTable'

function row(id: number, name: string, inventoryId: number, status: ExchangeStatusName, createdAt: string): ExchangeRequestEntry {
    return {
        id,
        memberId: id,
        memberName: name,
        memberIdentity: {name},
        inventoryId,
        inventoryName: inventoryId === 1 ? 'Helme' : 'Jacken',
        inventoryType: 'INTERNAL',
        status,
        reason: '',
        createdAt,
        updatedAt: createdAt,
    }
}

const ALL = [
    row(1, 'Anna Berger', 1, ExchangeStatus.ANNOUNCED, '2026-05-01T08:00:00Z'),
    row(2, 'Benno Klein', 2, ExchangeStatus.DONE, '2026-05-03T08:00:00Z'),
    row(3, 'Carla Anders', 1, ExchangeStatus.SHIPPED, '2026-05-02T08:00:00Z'),
    row(4, 'Dora Anders', 2, ExchangeStatus.ANNOUNCED, '2026-05-04T08:00:00Z'),
]

function table() {
    return useExchangeTable(() => ALL)
}

function exportOf(rows: () => ExchangeRequestEntry[]) {
    return useExport<ExchangeRequestEntry>({
        rows,
        rowId: r => r.id,
        columns: () => [],
        selectAllRows: true,
    })
}

describe('useExchangeTable', () => {
    it('starts on the open exchanges, newest first', () => {
        const {visible} = table()
        expect(visible.value.map(r => r.id)).toEqual([4, 3, 1])
    })

    it('keeps sorting and filtering working together', () => {
        const {visible, statuses, inventoryIds, selectSort} = table()
        statuses.value = []
        inventoryIds.value = ['2']
        selectSort('member')
        expect(visible.value.map(r => r.id)).toEqual([2, 4])
    })

    it('shows the rows of every status that was ticked', () => {
        const {visible, statuses} = table()
        statuses.value = [ExchangeStatus.SHIPPED, ExchangeStatus.DONE]
        expect(visible.value.map(r => r.id)).toEqual([2, 3])
    })

    it('shows the rows of every inventory that was ticked', () => {
        const {visible, statuses, inventoryIds} = table()
        statuses.value = []
        inventoryIds.value = ['1', '2']
        expect(visible.value.map(r => r.id)).toEqual([4, 2, 3, 1])
    })

    it('takes an emptied tick list as no restriction rather than as nothing left', () => {
        const {visible, statuses} = table()
        statuses.value = []
        expect(visible.value.map(r => r.id)).toEqual([4, 2, 3, 1])
    })

    it('says nothing is left rather than silently showing everything', () => {
        const {visible, search} = table()
        search.value = 'Zacharias'
        expect(visible.value).toEqual([])
    })
})

describe('the export selection', () => {
    it('takes exactly the rows the filter shows', () => {
        const {visible, inventoryIds} = table()
        inventoryIds.value = ['1']
        const {startExport, selectedRows} = exportOf(() => visible.value)
        startExport()
        expect(selectedRows.value.map(r => r.id)).toEqual([3, 1])
    })

    it('never carries a row the filter hides', () => {
        const {visible, inventoryIds} = table()
        const {startExport, selectedRows} = exportOf(() => visible.value)
        startExport()
        inventoryIds.value = ['2']
        expect(selectedRows.value.map(r => r.id)).toEqual([4])
    })

    it('carries the rows of every status ticked once they are all selected', () => {
        const {visible, statuses} = table()
        statuses.value = [ExchangeStatus.SHIPPED, ExchangeStatus.DONE]
        const {startExport, selectedRows} = exportOf(() => visible.value)
        startExport()
        expect(selectedRows.value.map(r => r.id)).toEqual([2, 3])
    })

    it('survives a change of order with the same rows selected', () => {
        const {visible, selectSort} = table()
        const {startExport, selectedIds, selectedRows} = exportOf(() => visible.value)
        startExport()
        const before = [...selectedIds.value].sort()

        selectSort('member')

        expect([...selectedIds.value].sort()).toEqual(before)
        expect(selectedRows.value.map(r => r.id)).toEqual([1, 3, 4])
    })
})
