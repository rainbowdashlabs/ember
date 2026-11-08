/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {TemplateQuestionKind, TextFormat, type QuestionLimits, type TemplateQuestion} from '@/api/generated/schema'
import {NO_LIMITS, anyAnswerProblem, answerProblem, dayOf} from './questionLimits'

const TODAY = '2026-10-02'

function question(kind: TemplateQuestionKind, limits: Partial<QuestionLimits>): TemplateQuestion {
    return {key: 'frage', label: 'Frage', kind, suggestion: '', required: false, limits: {...NO_LIMITS, ...limits}}
}

/** The limits of a question as the generation form checks them, by the rules the server checks them by. */
describe('question limits', () => {
    it('count a bound from today or take its fixed day', () => {
        expect(dayOf({day: null, fromToday: 30}, TODAY)).toBe('2026-11-01')
        expect(dayOf({day: null, fromToday: -2}, TODAY)).toBe('2026-09-30')
        expect(dayOf({day: '2027-01-01', fromToday: null}, TODAY)).toBe('2027-01-01')
        expect(dayOf(null, TODAY)).toBeNull()
    })

    it('find a day outside its bounds', () => {
        const days = question(TemplateQuestionKind.DATE, {earliest: {day: null, fromToday: 0}, latest: {day: null, fromToday: 14}})

        expect(answerProblem(days, '2026-10-01', TODAY)).toBe('dayOutOfRange')
        expect(answerProblem(days, '2026-10-17', TODAY)).toBe('dayOutOfRange')
        expect(answerProblem(days, '2026-10-16', TODAY)).toBeNull()
        expect(answerProblem(days, 'morgen', TODAY)).toBe('notADay')
    })

    it('find a text too long or of the wrong format', () => {
        const text = question(TemplateQuestionKind.TEXT, {maxLength: 5, format: TextFormat.POSTAL_CODE})

        expect(answerProblem(text, '123456', TODAY)).toBe('tooLong')
        expect(answerProblem(text, '1234a', TODAY)).toBe('wrongFormat')
        expect(answerProblem(text, '01067', TODAY)).toBeNull()
    })

    it('take a number with a point or a comma and find one outside its bounds', () => {
        const number = question(TemplateQuestionKind.NUMBER, {min: 1, max: 10, whole: true})

        expect(answerProblem(number, 'zwei', TODAY)).toBe('notANumber')
        expect(answerProblem(number, '11', TODAY)).toBe('numberOutOfRange')
        expect(answerProblem(number, '2,5', TODAY)).toBe('numberOutOfRange')
        expect(answerProblem(number, '2,0', TODAY)).toBeNull()
    })

    it('take a choice in any case and nothing else', () => {
        const choice = question(TemplateQuestionKind.CHOICE, {choices: ['Leitung']})

        expect(answerProblem(choice, 'leitung', TODAY)).toBeNull()
        expect(answerProblem(choice, 'Gast', TODAY)).toBe('notAChoice')
    })

    it('leave an empty answer to the required flag', () => {
        const number = question(TemplateQuestionKind.NUMBER, {min: 1})

        expect(answerProblem(number, '', TODAY)).toBeNull()
        expect(anyAnswerProblem([number], {}, TODAY)).toBe(false)
        expect(anyAnswerProblem([number], {frage: '0'}, TODAY)).toBe(true)
    })
})
