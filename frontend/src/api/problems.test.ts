/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {hasDetails, type ProblemEntry} from './problems'

function entry(over: Partial<ProblemEntry> = {}): ProblemEntry {
    return {
        id: 1,
        level: 'WARN',
        logger: 'dev.chojo.ember.api.ApiServer',
        exceptionClass: null,
        exceptionMessage: null,
        stacktrace: null,
        firstOccurrence: '2026-09-10T08:00:00Z',
        lastOccurrence: '2026-09-10T08:00:00Z',
        count: 1,
        acknowledged: false,
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
