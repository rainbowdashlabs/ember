/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {presentQuestions, shuffled} from './formShuffle'

/** Always draws the last position, which turns a list around. */
const reversing = () => 0

function question(id: number, pageKey: string, shuffle = false, config: Record<string, unknown> = {}) {
    return {id, pageKey, shuffle, config}
}

/**
 * Shuffling puts questions in another order within their page and never across pages, and options
 * in another order within their question, leaving what was handed in as it was.
 */
describe('formShuffle', () => {
    it('reorders a list without touching it', () => {
        const items = [1, 2, 3]
        expect(shuffled(items, reversing)).toEqual([2, 3, 1])
        expect(items).toEqual([1, 2, 3])
    })

    it('keeps the order of a form that shuffles nothing', () => {
        const questions = [question(1, 'a'), question(2, 'a'), question(3, 'b')]
        expect(presentQuestions(questions, false, reversing).map(q => q.id)).toEqual([1, 2, 3])
    })

    it('shuffles questions within their page and keeps the pages in order', () => {
        const questions = [question(1, 'a'), question(2, 'a'), question(3, 'b'), question(4, 'b')]
        const ids = presentQuestions(questions, true, reversing).map(q => q.id)
        expect(ids).toEqual([2, 1, 4, 3])
    })

    it('shuffles the options of a question set to shuffle, and only those', () => {
        const options = [{key: 'x', label: 'X'}, {key: 'y', label: 'Y'}]
        const statements = [{key: 's', label: 'S'}, {key: 't', label: 'T'}]
        const [mixed, plain] = presentQuestions(
            [question(1, 'a', true, {options, statements}), question(2, 'a', false, {options})], false, reversing)
        expect((mixed!.config.options as {key: string}[]).map(o => o.key)).toEqual(['y', 'x'])
        expect((mixed!.config.statements as {key: string}[]).map(o => o.key)).toEqual(['t', 's'])
        expect(plain!.config.options).toBe(options)
    })
})
