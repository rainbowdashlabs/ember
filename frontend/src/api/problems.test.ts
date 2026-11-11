/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {hasDetails, problemTitle} from './problems'
import {ProblemLevel, ProblemState, type StoredProblem} from './generated/schema'

function entry(over: Partial<StoredProblem> = {}): StoredProblem {
    return {
        id: 1,
        fingerprint: 'abc',
        level: ProblemLevel.WARN,
        message: '404 on GET /api/v1/members/17',
        logger: 'dev.chojo.ember.api.ApiServer',
        exceptionClass: null,
        exceptionMessage: null,
        stacktrace: null,
        firstSeen: '2026-09-10T08:00:00Z',
        lastSeen: '2026-09-10T08:00:00Z',
        count: 1,
        state: ProblemState.OPEN,
        stateChangedAt: null,
        reopenedCount: 0,
        distinctMessages: ['404 on GET /api/v1/members/17'],
        ...over,
    }
}

describe('hasDetails', () => {
    it('says an entry with a stacktrace has something behind it', () => {
        expect(hasDetails(entry({stacktrace: 'java.lang.IllegalStateException\n    at Foo.bar'}))).toBe(true)
    })

    it('says an entry with more messages than the one shown has something behind it', () => {
        expect(hasDetails(entry({distinctMessages: ['first', 'second']}))).toBe(true)
    })

    it('says a single message with no stacktrace has nothing behind it', () => {
        expect(hasDetails(entry())).toBe(false)
    })

    it('says an entry with no messages at all has nothing behind it', () => {
        expect(hasDetails(entry({distinctMessages: []}))).toBe(false)
    })
})

describe('problemTitle', () => {
    it('names an exception by its class and message', () => {
        expect(problemTitle(entry({exceptionClass: 'java.lang.IllegalStateException', exceptionMessage: 'broken'})))
            .toBe('java.lang.IllegalStateException: broken')
    })

    it('cuts a long line so it cannot flood a card or a dialog', () => {
        const title = problemTitle(entry({distinctMessages: ['x'.repeat(500)]}))

        expect(title).toHaveLength(203)
        expect(title.endsWith('...')).toBe(true)
    })
})
