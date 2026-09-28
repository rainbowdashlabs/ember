/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {newOptionKey, numberedOptions, optionKeysOf, optionLabel, optionsOf} from './formOptions'
import {formatAnswerDisplay} from './formAnswerDisplay'
import {QuestionTypes} from '@/api/forms'

/**
 * Options are read by key wherever an answer is shown, so an answer keeps meaning what it meant.
 *
 * @vitest-environment happy-dom
 */
describe('formOptions', () => {
    it('reads the keyed options of a question and skips anything else in the list', () => {
        const config = {options: [{key: 'a', label: 'Ja'}, 'Nein', null]}
        expect(optionsOf(config)).toEqual([{key: 'a', label: 'Ja'}])
        expect(optionsOf({})).toEqual([])
    })

    it('finds the keys of a choice and of a Likert grid alike', () => {
        expect(optionKeysOf({options: numberedOptions('Ja', 'Nein')})).toEqual(['o0', 'o1'])
        expect(optionKeysOf({statements: numberedOptions('Essen')})).toEqual(['o0'])
        expect(optionKeysOf({longAnswer: true})).toEqual([])
    })

    it('makes a key the question does not have yet', () => {
        const taken = numberedOptions('Ja', 'Nein')
        const key = newOptionKey(taken)
        expect(key).toMatch(/^[0-9a-z]{8}$/)
        expect(optionLabel(taken, key)).toBeUndefined()
        expect(optionLabel(taken, 'o1')).toBe('Nein')
    })
})

describe('formatAnswerDisplay', () => {
    const reordered = {options: [{key: 'b', label: 'Nein'}, {key: 'a', label: 'Ja'}]}

    it('names the options a choice picked by key, whatever their order now', () => {
        expect(formatAnswerDisplay(QuestionTypes.CHOICE, reordered, '{"selected":["a"],"other":"Später"}'))
            .toBe('Ja, Sonstige: Später')
    })

    it('shows a key the question no longer has as itself', () => {
        expect(formatAnswerDisplay(QuestionTypes.CHOICE, reordered, '{"selected":["gone"]}')).toBe('#gone')
    })

    it('lists a ranking in the order it was given', () => {
        expect(formatAnswerDisplay(QuestionTypes.RANKING, reordered, '{"order":["a","b"]}')).toBe('1. Ja, 2. Nein')
    })

    it('lists Likert ratings by statement, in the order of the statements', () => {
        const config = {statements: [{key: 's2', label: 'Zelte'}, {key: 's1', label: ''}]}
        expect(formatAnswerDisplay(QuestionTypes.LIKERT, config, '{"ratings":{"s1":4,"s2":2}}'))
            .toBe('Zelte: 2, Option 2: 4')
    })
})
