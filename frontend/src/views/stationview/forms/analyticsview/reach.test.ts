/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {answeredLine, reachLines} from './reach'

const t = (key: string, named?: Record<string, unknown>) => `${key} ${JSON.stringify(named ?? {})}`

/** A question only some readers were shown says so, and one everybody saw does not. */
describe('reach', () => {
    it('says how many were shown a question only some reached', () => {
        expect(answeredLine({questionId: 1, answerCount: 3, reachedCount: 4}, 10, t))
            .toBe('forms.analytics.answeredOfReached {"answered":3,"reached":4,"total":10}')
    })

    it('says nothing about reach where everybody saw the question', () => {
        expect(answeredLine({questionId: 1, answerCount: 3, reachedCount: 10}, 10, t)).toBe('forms.analytics.answered {"count":3}')
        expect(answeredLine(undefined, 10, t)).toBe('forms.analytics.answered {"count":0}')
    })

    it('names only the groups that did not all see the question', () => {
        const groups = [
            {key: 'a', label: 'A', responseCount: 5, tallies: [{questionId: 1, answerCount: 2, reachedCount: 2}]},
            {key: 'b', label: 'B', responseCount: 3, tallies: [{questionId: 1, answerCount: 3, reachedCount: 3}]},
        ]
        expect(reachLines(1, groups, ['Jugend', 'Aktive'], t))
            .toEqual(['forms.analytics.groupReached {"group":"Jugend","reached":2,"total":5}'])
    })
})
