/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {questionChips} from './settingChips'
import type {QuestionDraft} from './types'
import type {QuestionType} from '@/api/forms'

const t = (key: string, named?: Record<string, unknown>) => named ? `${key} ${JSON.stringify(named)}` : key

function question(questionType: QuestionType, config: Record<string, unknown>, shuffle = false): QuestionDraft {
    return {id: 'temp-1', questionType, title: '', description: '', required: false, shuffle, config, branch: null}
}

/**
 * A setting behind the menu shows on the tile once it differs from its default, and a question left
 * at its defaults shows nothing.
 */
describe('questionChips', () => {
    it('shows nothing for a question at its defaults', () => {
        expect(questionChips(question('CHOICE', {multiSelect: false, dropdown: false, allowOther: false}), t))
            .toEqual([])
        expect(questionChips(question('RATING', {scale: 5, icon: 'STAR'}), t)).toEqual([])
        expect(questionChips(question('LIKERT', {scaleMin: 1, scaleMax: 5, scaleLabels: []}), t)).toEqual([])
        expect(questionChips(question('TEXT', {longAnswer: false}), t)).toEqual([])
    })

    it('names the limit of a multiple choice', () => {
        const chips = questionChips(question('CHOICE', {multiSelect: true, multiLimitType: 'AT_MOST', multiLimit: 3}), t)
        expect(chips).toEqual([
            'forms.chips.multiSelectLimited {"limit":"forms.chips.limit.AT_MOST {\\"count\\":3}"}',
        ])
    })

    it('lists every changed setting of a choice', () => {
        expect(questionChips(question('CHOICE', {multiSelect: true, dropdown: true, allowOther: true}, true), t))
            .toEqual(['forms.chips.multiSelect', 'forms.chips.dropdown', 'forms.chips.allowOther', 'forms.chips.shuffled'])
    })

    it('says a rating runs on another scale or with another icon', () => {
        expect(questionChips(question('RATING', {scale: 10, icon: 'HEART'}), t))
            .toEqual(['forms.chips.ratingScale {"max":10}', 'forms.chips.ratingIcon.HEART'])
    })

    it('says a Likert grid has its own range and labels', () => {
        expect(questionChips(question('LIKERT', {scaleMin: 0, scaleMax: 4, scaleLabels: ['', 'gut']}), t))
            .toEqual(['forms.chips.likertScale {"min":0,"max":4}', 'forms.chips.likertLabels'])
    })

    it('offers shuffling only where options can be shuffled', () => {
        expect(questionChips(question('TEXT', {longAnswer: true}, true), t)).toEqual(['forms.chips.longAnswer'])
        expect(questionChips(question('RANKING', {}, true), t)).toEqual(['forms.chips.shuffled'])
    })
})
