/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {emptyAnswer, isEmptyAnswer} from './formAnswers'

/**
 * Every question starts with an answer of the shape the server reads, and that answer counts as
 * empty until something is given.
 */
describe('formAnswers', () => {
    const types = ['CHOICE', 'TEXT', 'RATING', 'DATE', 'LIKERT']

    it('starts every kind of question empty', () => {
        for (const type of types) expect(isEmptyAnswer(type, emptyAnswer(type, {}))).toBe(true)
    })

    it('starts a ranking in the order of its options, which is an answer already', () => {
        const ranking = emptyAnswer('RANKING', {options: [{key: 'a', label: 'A'}, {key: 'b', label: 'B'}]})
        expect(ranking).toEqual({order: ['a', 'b']})
        expect(isEmptyAnswer('RANKING', ranking)).toBe(false)
    })

    it('counts an answer once something is given', () => {
        expect(isEmptyAnswer('CHOICE', {selected: ['a'], other: ''})).toBe(false)
        expect(isEmptyAnswer('CHOICE', {selected: [], other: 'selbst'})).toBe(false)
        expect(isEmptyAnswer('TEXT', {text: '  '})).toBe(true)
        expect(isEmptyAnswer('TEXT', {text: 'Ja'})).toBe(false)
        expect(isEmptyAnswer('RATING', {rating: 3})).toBe(false)
        expect(isEmptyAnswer('DATE', {date: '2026-10-01'})).toBe(false)
        expect(isEmptyAnswer('LIKERT', {ratings: {s: 2}})).toBe(false)
    })

    it('counts a missing answer as empty', () => {
        expect(isEmptyAnswer('TEXT', undefined)).toBe(true)
    })
})
