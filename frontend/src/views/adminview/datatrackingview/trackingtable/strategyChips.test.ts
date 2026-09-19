/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import type {DataTracking, TableEntry} from '@/api/dataTracking'
import {isCascadeMisleading, strategyChipsOf} from './strategyChips'

function entry(strategies: {strategy: string, column: string}[], foreignKeys: TableEntry['foreignKeys'] = []): TableEntry {
    return {
        tableHash: 'hash',
        columns: [],
        foreignKeys,
        gdprDeletion: {strategies},
    } as unknown as TableEntry
}

function tracking(tables: Record<string, TableEntry>): DataTracking {
    return {tables} as unknown as DataTracking
}

/**
 * The deletion strategies a tracked table shows, and the warning a cascade earns when the table it
 * hangs on is never deleted from.
 */
describe('strategyChipsOf', () => {
    it('shows each strategy on a column once', () => {
        const chips = strategyChipsOf(entry([
            {strategy: 'ANONYMIZE', column: 'name'},
            {strategy: 'ANONYMIZE', column: 'name'},
            {strategy: 'NULL', column: 'email'},
        ]), null)

        expect(chips.map(chip => `${chip.strategy}:${chip.column}`)).toEqual(['ANONYMIZE:name', 'NULL:email'])
    })

    it('names the strongest strategy of the table a cascade hangs on', () => {
        const child = entry([{strategy: 'CASCADE', column: 'member_id'}],
            [{column: 'member_id', refTable: 'member', refColumn: 'id', onDelete: 'CASCADE'}] as TableEntry['foreignKeys'])
        const parent = entry([{strategy: 'RETAIN', column: 'id'}, {strategy: 'DELETE_EXPLICIT', column: 'id'}])

        const [chip] = strategyChipsOf(child, tracking({member: parent}))

        expect(chip!.cascadeFrom).toEqual({table: 'member', effective: 'DELETE_EXPLICIT'})
        expect(isCascadeMisleading(chip!)).toBe(false)
    })

    it('warns where the parent is never deleted, so the cascade never fires', () => {
        const child = entry([{strategy: 'CASCADE', column: 'member_id'}],
            [{column: 'member_id', refTable: 'member', refColumn: 'id', onDelete: 'CASCADE'}] as TableEntry['foreignKeys'])

        const [chip] = strategyChipsOf(child, tracking({member: entry([{strategy: 'ANONYMIZE', column: 'id'}])}))

        expect(isCascadeMisleading(chip!)).toBe(true)
    })
})
