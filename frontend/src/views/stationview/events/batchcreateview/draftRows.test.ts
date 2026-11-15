/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {localInputToInstant} from '@/util/format'
import {draftProblems, draftToBatchRow, type DraftRow} from './draftRows'

function draft(change: Partial<DraftRow>): DraftRow {
    return {date: '2026-12-04', startTime: '16:30', endTime: '19:00', name: '', fieldValues: {}, ...change}
}

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

describe('draftToBatchRow', () => {
    it('places a row on the reader\'s clock with its own title', () => {
        expect(draftToBatchRow(draft({name: ' Adventszauber '}))).toEqual({
            name: 'Adventszauber',
            startTime: localInputToInstant('2026-12-04T16:30'),
            endTime: localInputToInstant('2026-12-04T19:00'),
            fieldValues: {},
        })
    })
})
