/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {localInputToInstant} from '@/util/format'
import {draftProblems, draftsToBatchRows, nextDraftRow, type DraftRow} from './draftRows'

function draft(change: Partial<DraftRow>): DraftRow {
    return {date: '2026-12-04', startTime: '16:30', endTime: '19:00', name: '', fieldValues: {}, ...change}
}

describe('nextDraftRow', () => {
    it('starts empty as the first row', () => {
        expect(nextDraftRow([])).toEqual({key: 0, date: '', startTime: '', endTime: '', name: '', fieldValues: {}})
    })

    it('takes the day and the times of the last row, not its title, under a key of its own', () => {
        const rows = [{...draft({}), key: 3}, {...draft({name: 'Adventszauber', date: '2026-12-05'}), key: 1}]
        expect(nextDraftRow(rows))
            .toEqual({key: 4, date: '2026-12-05', startTime: '16:30', endTime: '19:00', name: '', fieldValues: {}})
    })
})

describe('draftProblems', () => {
    it('finds nothing wrong with a complete row', () => {
        expect(draftProblems(draft({}))).toEqual([])
    })

    it('names a missing day before anything else', () => {
        expect(draftProblems(draft({date: '', startTime: ''}))).toEqual(['date'])
    })

    it('names missing times', () => {
        expect(draftProblems(draft({startTime: '', endTime: ''}))).toEqual(['startTime', 'endTime'])
    })

    it('names an end before the start', () => {
        expect(draftProblems(draft({startTime: '19:00', endTime: '16:30'}))).toEqual(['order'])
    })

    it('names an end at the very moment of the start', () => {
        expect(draftProblems(draft({startTime: '18:22', endTime: '18:22'}))).toEqual(['order'])
    })
})

describe('draftsToBatchRows', () => {
    it('places every row on the reader\'s clock with its own title', () => {
        expect(draftsToBatchRows([draft({name: ' Adventszauber '}), draft({date: '2026-12-05'})])).toEqual({
            rows: [
                {
                    name: 'Adventszauber',
                    startTime: localInputToInstant('2026-12-04T16:30'),
                    endTime: localInputToInstant('2026-12-04T19:00'),
                    fieldValues: {},
                },
                {
                    name: null,
                    startTime: localInputToInstant('2026-12-05T16:30'),
                    endTime: localInputToInstant('2026-12-05T19:00'),
                    fieldValues: {},
                },
            ],
            problems: [],
        })
    })

    it('creates nothing while one row cannot become an appointment', () => {
        expect(draftsToBatchRows([draft({}), draft({date: ''})]))
            .toEqual({rows: [], problems: [{index: 1, reason: 'date'}]})
    })
})
