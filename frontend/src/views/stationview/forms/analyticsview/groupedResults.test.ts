/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {QuestionTypes, type FormQuestionInfo, type FormQuestionTally, type FormResultGroup} from '@/api/forms'
import {averageRating, formatValue, matrixOf} from './groupedResults'
import {groupedBarOption, type GroupSeries} from './groupedChart'

function question(questionType: string, config: Record<string, unknown>): FormQuestionInfo {
    return {questionId: 1, questionType, title: 'Frage', config}
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
        const choice = question(QuestionTypes.CHOICE, {options: ['Ja', 'Nein'], allowOther: true})
        const groups = [
            group('youth', {answerCount: 4, optionCounts: [3, 1], otherCount: 0}),
            group('active', {answerCount: 20, optionCounts: [5, 14], otherCount: 1}),
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
        const ranking = question(QuestionTypes.RANKING, {options: ['A', 'B']})
        const matrix = matrixOf(ranking, [group('x', {answerCount: 2, rankingScores: [4, 2]})], '')!

        expect(matrix.unit).toBe('average')
        expect(matrix.values).toEqual([[2], [1]])
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
        const matrix = matrixOf(question(QuestionTypes.CHOICE, {options: ['Ja']}), [
            group('a', {answerCount: 1, optionCounts: [1]}),
            group('b', {answerCount: 2, optionCounts: [1]}),
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
