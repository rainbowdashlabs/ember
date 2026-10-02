/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {QuestionType} from '@/api/forms'
import {emptyAnswer, isEmptyAnswer, storedAnswer} from './formAnswers'

/**
 * Every question starts with an answer of the shape the server reads, and that answer counts as
 * empty until something is given.
 */
describe('formAnswers', () => {
    const types: QuestionType[] = ['CHOICE', 'TEXT', 'RATING', 'DATE', 'LIKERT']

    it('starts every kind of question empty', () => {
        for (const type of types) expect(isEmptyAnswer(emptyAnswer(type, {}))).toBe(true)
    })

    it('starts a ranking in the order of its options, which is an answer already', () => {
        const ranking = emptyAnswer('RANKING', {options: [{key: 'a', label: 'A'}, {key: 'b', label: 'B'}]})
        expect(ranking).toEqual({type: 'RANKING', order: ['a', 'b']})
        expect(isEmptyAnswer(ranking)).toBe(false)
    })

    it('counts an answer once something is given', () => {
        expect(isEmptyAnswer({type: 'CHOICE', selected: ['a'], other: ''})).toBe(false)
        expect(isEmptyAnswer({type: 'CHOICE', selected: [], other: 'selbst'})).toBe(false)
        expect(isEmptyAnswer({type: 'TEXT', text: '  '})).toBe(true)
        expect(isEmptyAnswer({type: 'TEXT', text: 'Ja'})).toBe(false)
        expect(isEmptyAnswer({type: 'RATING', rating: 3})).toBe(false)
        expect(isEmptyAnswer({type: 'DATE', date: '2026-10-01'})).toBe(false)
        expect(isEmptyAnswer({type: 'LIKERT', ratings: {s: 2}})).toBe(false)
    })

    it('counts a missing answer as empty', () => {
        expect(isEmptyAnswer(undefined)).toBe(true)
    })

    it('reads a stored answer back for the question it answers', () => {
        expect(storedAnswer('TEXT', {}, '{"type":"TEXT","text":"Ja"}')).toEqual({type: 'TEXT', text: 'Ja'})
        expect(storedAnswer('RATING', {}, '{"rating":4}')).toEqual({type: 'RATING', rating: 4})
    })

    it('starts empty where the stored answer cannot be read or answers another kind of question', () => {
        expect(storedAnswer('TEXT', {}, 'kaputt')).toEqual({type: 'TEXT', text: ''})
        expect(storedAnswer('TEXT', {}, '{"type":"DATE","date":"2026-10-01"}')).toEqual({type: 'TEXT', text: ''})
    })
})
