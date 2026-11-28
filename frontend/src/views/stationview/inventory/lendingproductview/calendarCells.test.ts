/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {RemoteDay} from '@/api/generated/schema'
import {inChosen, monthCells, pickDay, shiftMonth, stateOf} from './calendarCells'

function day(date: string, counts: [artId: number | null, free: number, offered: number][], blocked = false): RemoteDay {
    return {
        day: date,
        blocked,
        counts: counts.map(([artId, free, offered]) => ({artId, itemId: null, singles: false, free, offered})),
    }
}

/** The calendar's arithmetic: what a day shows, where a month starts, and how two clicks pick days. */
describe('calendarCells', () => {
    it('shows a day as all, some or none free for the chosen rows, and a blocked day as blocked', () => {
        const mixed = day('2030-06-12', [[1, 2, 2], [2, 0, 3]])

        expect(stateOf(mixed, 'all')).toBe('some')
        expect(stateOf(mixed, '1')).toBe('all')
        expect(stateOf(mixed, '2')).toBe('none')
        expect(stateOf(day('2030-06-13', [[1, 2, 2]], true), 'all')).toBe('blocked')
    })

    it('starts a month on its weekday, weeks running from Monday', () => {
        const cells = monthCells([day('2030-06-01', [[1, 1, 1]]), day('2030-06-02', [[1, 1, 1]])], 'all')

        expect(cells.filter(cell => cell.kind === 'blank')).toHaveLength(5)
        expect(cells.at(-1)).toMatchObject({kind: 'day', day: '2030-06-02', number: 2})
    })

    it('sets the first day, then the last, and starts again on a click before or after both', () => {
        expect(pickDay('2030-06-12', '', '')).toEqual({from: '2030-06-12', to: ''})
        expect(pickDay('2030-06-14', '2030-06-12', '')).toEqual({from: '2030-06-12', to: '2030-06-14'})
        expect(pickDay('2030-06-10', '2030-06-12', '')).toEqual({from: '2030-06-10', to: ''})
        expect(pickDay('2030-06-20', '2030-06-12', '2030-06-14')).toEqual({from: '2030-06-20', to: ''})
        expect(inChosen('2030-06-13', '2030-06-12', '2030-06-14')).toBe(true)
        expect(inChosen('2030-06-12', '2030-06-12', '')).toBe(true)
        expect(inChosen('2030-06-13', '2030-06-12', '')).toBe(false)
    })

    it('moves between months across a year', () => {
        expect(shiftMonth('2030-12', 1)).toBe('2031-01')
        expect(shiftMonth('2030-01', -1)).toBe('2029-12')
    })
})
