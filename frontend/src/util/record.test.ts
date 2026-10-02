/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {withoutKey} from './record'

/**
 * Leaving an entry out copies the record and never touches the one handed in.
 *
 * @vitest-environment happy-dom
 */
describe('withoutKey', () => {
    it('leaves out the named entry and keeps the rest', () => {
        expect(withoutKey({a: 1, b: 2}, 'a')).toEqual({b: 2})
    })

    it('leaves the record it was given as it was', () => {
        const record = {a: 1, b: 2}
        withoutKey(record, 'a')
        expect(record).toEqual({a: 1, b: 2})
    })

    it('finds numeric keys, which a record holds as strings', () => {
        expect(withoutKey<number, string>({1: 'one', 2: 'two'}, 1)).toEqual({2: 'two'})
    })
})
