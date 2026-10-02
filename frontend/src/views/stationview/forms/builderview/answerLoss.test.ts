/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {answerLoss} from './useAnswerLossConsent'
import type {QuestionAnswerCount} from '@/api/generated/schema'

/**
 * One question before a save asks about everything it throws away: the answers of removed
 * questions, and the choices of options removed from questions that stay.
 */
describe('answerLoss', () => {
    const counts: QuestionAnswerCount[] = [
        {questionId: 1, answers: 5, optionAnswers: {}},
        {questionId: 2, answers: 4, optionAnswers: {yes: 3, no: 2, maybe: 0}},
    ]

    it('adds up the answers of removed questions', () => {
        expect(answerLoss(counts, {questionIds: [1], options: []})).toEqual({answers: 5, selections: 0})
    })

    it('adds up how often removed options were chosen', () => {
        expect(answerLoss(counts, {questionIds: [], options: [{questionId: 2, keys: ['no', 'maybe']}]}))
            .toEqual({answers: 0, selections: 2})
    })

    it('counts both kinds of loss of one save together', () => {
        expect(answerLoss(counts, {questionIds: [1], options: [{questionId: 2, keys: ['yes']}]}))
            .toEqual({answers: 5, selections: 3})
    })
})
