/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {answerProblemsOf, placeProblems} from './answerProblems'

const t = (key: string) => (key === 'refusal.F-059' ? 'Diese Frage muss beantwortet werden' : key)

/** Refused answers are shown on the first page of the reader's path that has one, at their question. */
describe('answerProblems', () => {
    it('reads the problems a refusal names', () => {
        const problem = {questionId: 1, pageKey: 'p0', code: 'F-059', message: 'This question needs an answer'}
        expect(answerProblemsOf({response: {status: 400, data: {problems: [problem]}}})).toEqual([problem])
        expect(answerProblemsOf(new Error('offline'))).toEqual([])
    })

    it('goes back to the first page with a problem and says it in German where it can', () => {
        const placed = placeProblems(['p0', 'p2', 'p3'], [
            {questionId: 7, pageKey: 'p3', code: 'F-060', message: 'This answer does not fit the question'},
            {questionId: 4, pageKey: 'p2', code: 'F-059', message: 'This question needs an answer'},
        ], t)

        expect(placed.walked).toEqual(['p0', 'p2'])
        expect(placed.marked).toEqual({
            4: 'Diese Frage muss beantwortet werden',
            7: 'This answer does not fit the question',
        })
    })

    it('stays on the page shown for a problem that names no page', () => {
        const placed = placeProblems(['p0', 'p1'], [{questionId: 9, pageKey: null, code: 'F-061', message: 'x'}], t)
        expect(placed.walked).toEqual(['p0', 'p1'])
    })
})
