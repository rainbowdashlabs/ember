/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {describe, expect, it} from 'vitest'
import {MovementPurpose, MovementState, StepActor, type Movement} from '@/api/movements'
import {useDataTable, type DataTableApi} from '@/composables/useDataTable'
import {MovementColumn, useMovementColumns} from './movementColumns'

function movement(id: number, fields: Partial<Movement>): Movement {
    return {
        id,
        purpose: MovementPurpose.ISSUE,
        state: MovementState.OPEN,
        createdAt: '2026-01-01T10:00:00Z',
        ...fields,
    } as Movement
}

const MOVEMENTS = [
    movement(1, {inventoryName: 'Helme', itemName: 'Helm 3', currentStepActor: StepActor.STATION, reachedStepLabel: 'Bestellt'}),
    movement(2, {inventoryName: 'Jacken', itemName: 'Jacke 7', currentStepActor: StepActor.MEMBER, reachedStepLabel: 'Ausgegeben'}),
    movement(3, {inventoryName: 'Helme', itemName: 'Helm 9', state: MovementState.DONE, reachedStepLabel: 'Ausgegeben'}),
]

function queue(): DataTableApi<Movement> {
    let api: DataTableApi<Movement> | null = null
    mount(defineComponent({
        setup() {
            api = useDataTable<Movement>({
                id: 'movements-test', rows: MOVEMENTS, columns: useMovementColumns(() => MOVEMENTS), rowKey: m => m.id,
            })
            return () => null
        },
    }))
    return api as unknown as DataTableApi<Movement>
}

function ids(table: DataTableApi<Movement>): number[] {
    return table.rows.map(row => row.id).toSorted()
}

describe('useMovementColumns', () => {
    it('narrows what a row is about by its inventory', () => {
        const table = queue()
        table.openFilter(MovementColumn.SUBJECT)

        expect(table.filterDialog?.choices.map(choice => choice.label)).toEqual(['Helme', 'Jacken'])
        table.setFilter(MovementColumn.SUBJECT, new Set(['Helme']), false)
        expect(ids(table)).toEqual([1, 3])
    })

    it('offers the steps by their own names beside the states and turns', () => {
        const table = queue()
        table.openFilter(MovementColumn.STANDING)

        const labels = table.filterDialog?.choices.map(choice => choice.label) ?? []
        expect(labels).toEqual(expect.arrayContaining(['Offen', 'Abgeschlossen', 'Ausgegeben', 'Bestellt']))
    })

    it('finds every movement that reached a step, running or not', () => {
        const table = queue()
        table.setFilter(MovementColumn.STANDING, new Set(['step:Ausgegeben']), false)

        expect(ids(table)).toEqual([2, 3])
    })

    it('keeps to the running ones where only the state is asked for', () => {
        const table = queue()
        table.setFilter(MovementColumn.STANDING, new Set([MovementState.OPEN]), false)

        expect(ids(table)).toEqual([1, 2])
    })
})
