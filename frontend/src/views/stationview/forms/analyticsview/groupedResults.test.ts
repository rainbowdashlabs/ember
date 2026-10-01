/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {QuestionTypes, type QuestionType} from '@/api/forms'
import type {FormQuestionInfo, FormQuestionTally, FormResultGroup} from '@/api/generated/schema'
import {averageRating, formatValue, matrixOf} from './groupedResults'
import {groupedBarOption, type GroupSeries} from './groupedChart'
import {numberedOptions} from '@/util/formOptions'
import {questionConfigOf} from '../builderview/questionDefaults'

function question(questionType: QuestionType, config: Record<string, unknown>): FormQuestionInfo {
    return {questionId: 1, questionType, title: 'Frage', config: questionConfigOf(questionType, config)}
}

function group(key: string, tally: Omit<FormQuestionTally, 'questionId'>): FormResultGroup {
    return {key, label: key, responseCount: tally.answerCount, tallies: [{questionId: 1, ...tally}]}
}

/**
 * One question's counts laid out for comparing groups, which the grouped chart and the table view
 * both draw from.
 */
describe('groupedResults', () => {
    it('compares choices as the share of each group rather than as raw counts', () => {
        const choice = question(QuestionTypes.CHOICE, {options: numberedOptions('Ja', 'Nein'), allowOther: true})
        const groups = [
            group('youth', {answerCount: 4, optionCounts: {o0: 3, o1: 1}, otherCount: 0}),
            group('active', {answerCount: 20, optionCounts: {o0: 5, o1: 14}, otherCount: 1}),
        ]

        const matrix = matrixOf(choice, groups, 'Sonstiges')!

        expect(matrix.rows).toEqual(['Ja', 'Nein', 'Sonstiges'])
        expect(matrix.unit).toBe('percent')
        expect(matrix.values[0]).toEqual([75, 25])
        expect(matrix.counts[0]).toEqual([3, 5])
        expect(matrix.answers).toEqual([4, 20])
    })

    it('leaves a share empty for a group that gave no answer', () => {
        const rating = question(QuestionTypes.RATING, {scale: 3})
        const matrix = matrixOf(rating, [group('a', {answerCount: 2, ratingCounts: [0, 1, 1]}), group('b', {answerCount: 0, ratingCounts: [0, 0, 0]})], '')!

        expect(matrix.rows).toEqual(['1', '2', '3'])
        expect(matrix.values[2]).toEqual([50, null])
    })

    it('compares rankings by average points per answer', () => {
        const ranking = question(QuestionTypes.RANKING, {options: numberedOptions('A', 'B')})
        const matrix = matrixOf(ranking, [group('x', {answerCount: 2, rankingScores: {o0: 4, o1: 2}})], '')!

        expect(matrix.unit).toBe('average')
        expect(matrix.values).toEqual([[2], [1]])
    })

    it('reads each option by its key, in the order the question lists them now', () => {
        const reordered = question(QuestionTypes.CHOICE, {options: [{key: 'b', label: 'Nein'}, {key: 'a', label: 'Ja'}]})
        const matrix = matrixOf(reordered, [group('x', {answerCount: 4, optionCounts: {a: 3, b: 1}})], '')!

        expect(matrix.rows).toEqual(['Nein', 'Ja'])
        expect(matrix.counts).toEqual([[1], [3]])
    })

    it('averages Likert statements by statement key', () => {
        const likert = question(QuestionTypes.LIKERT, {statements: numberedOptions('Essen', '')})
        const matrix = matrixOf(likert, [group('x', {answerCount: 2, statementAverages: {o0: 4.5}})], '')!

        expect(matrix.rows).toEqual(['Essen', 'Option 2'])
        expect(matrix.values).toEqual([[4.5], [null]])
    })

    it('lists written answers instead of laying them out', () => {
        expect(matrixOf(question(QuestionTypes.TEXT, {}), [group('x', {answerCount: 1, values: ['Gut']})], '')).toBeNull()
    })

    it('averages a rating group from its counts', () => {
        expect(averageRating({questionId: 1, answerCount: 3, ratingCounts: [1, 0, 2]})).toBe(2.3)
        expect(averageRating(undefined)).toBeNull()
    })

    it('writes values the way a German reader reads them', () => {
        expect(formatValue(42.5, 'percent', 'de-DE')).toBe('42,5 %')
        expect(formatValue(null, 'average', 'de-DE')).toBe('-')
    })

    it('draws one coloured series per group, labelled on every bar', () => {
        const matrix = matrixOf(question(QuestionTypes.CHOICE, {options: numberedOptions('Ja')}), [
            group('a', {answerCount: 1, optionCounts: {o0: 1}}),
            group('b', {answerCount: 2, optionCounts: {o0: 1}}),
        ], '')!
        const series: GroupSeries[] = [
            {key: 'a', name: 'Jugend', color: '#2a78d6', responseCount: 1},
            {key: 'b', name: 'Aktive', color: '#eb6834', responseCount: 2},
        ]

        const option = groupedBarOption(matrix, series, '#333', 'de-DE')

        expect(option.series.map(s => s.name)).toEqual(['Jugend', 'Aktive'])
        expect(option.series.map(s => s.itemStyle.color)).toEqual(['#2a78d6', '#eb6834'])
        expect(option.series.every(s => s.label.show)).toBe(true)
        expect(option.xAxis.max).toBe(100)
    })
})
