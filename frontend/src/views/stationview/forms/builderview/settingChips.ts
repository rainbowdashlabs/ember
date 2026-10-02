/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FormQuestionType} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'
import type {QuestionDraft} from './types'

const DEFAULT_RATING_SCALE = 5
const DEFAULT_RATING_ICON = 'STAR'
const DEFAULT_LIKERT_MIN = 1
const DEFAULT_LIKERT_MAX = 5

/** The kinds of question whose options can be shuffled for every reader. */
const SHUFFLEABLE: readonly string[] = [FormQuestionType.CHOICE, FormQuestionType.RANKING, FormQuestionType.LIKERT]

/** Whether the options of this kind of question can be put in a different order for every reader. */
export function canShuffle(type: string): boolean {
    return SHUFFLEABLE.includes(type)
}

function choiceLimit(config: Record<string, unknown>, t: Translate): string | null {
    const type = config.multiLimitType as string | undefined
    const limit = config.multiLimit as number | null | undefined
    if (!limit || !type || type === 'NONE') return null
    return t(`forms.chips.limit.${type}`, {count: limit})
}

function choiceChips(config: Record<string, unknown>, t: Translate): string[] {
    const chips: string[] = []
    if (config.multiSelect) {
        const limit = choiceLimit(config, t)
        chips.push(limit ? t('forms.chips.multiSelectLimited', {limit}) : t('forms.chips.multiSelect'))
    }
    if (config.dropdown) chips.push(t('forms.chips.dropdown'))
    if (config.allowOther) chips.push(t('forms.chips.allowOther'))
    return chips
}

function ratingChips(config: Record<string, unknown>, t: Translate): string[] {
    const chips: string[] = []
    const scale = (config.scale as number | undefined) ?? DEFAULT_RATING_SCALE
    if (scale !== DEFAULT_RATING_SCALE) chips.push(t('forms.chips.ratingScale', {max: scale}))
    const icon = (config.icon as string | undefined) ?? DEFAULT_RATING_ICON
    if (icon !== DEFAULT_RATING_ICON) chips.push(t(`forms.chips.ratingIcon.${icon}`))
    return chips
}

function likertChips(config: Record<string, unknown>, t: Translate): string[] {
    const chips: string[] = []
    const min = (config.scaleMin as number | undefined) ?? DEFAULT_LIKERT_MIN
    const max = (config.scaleMax as number | undefined) ?? DEFAULT_LIKERT_MAX
    if (min !== DEFAULT_LIKERT_MIN || max !== DEFAULT_LIKERT_MAX) chips.push(t('forms.chips.likertScale', {min, max}))
    const labels = (config.scaleLabels as string[] | undefined) ?? []
    if (labels.some(label => label?.trim())) chips.push(t('forms.chips.likertLabels'))
    return chips
}

function typeChips(question: QuestionDraft, t: Translate): string[] {
    switch (question.questionType) {
        case FormQuestionType.CHOICE: return choiceChips(question.config, t)
        case FormQuestionType.TEXT: return question.config.longAnswer ? [t('forms.chips.longAnswer')] : []
        case FormQuestionType.RATING: return ratingChips(question.config, t)
        case FormQuestionType.LIKERT: return likertChips(question.config, t)
        default: return []
    }
}

/**
 * The settings of a question that differ from their defaults, each as a short label.
 *
 * <p>Everything beyond the title, the content and "required" sits behind the question's menu. A
 * setting that is changed there and then hidden is the one nobody finds again, so every one that is
 * not at its default shows on the tile. A question with nothing changed shows nothing, which is what
 * keeps the tile slim.
 */
export function questionChips(question: QuestionDraft, t: Translate): string[] {
    const chips = typeChips(question, t)
    if (question.shuffle && canShuffle(question.questionType)) chips.push(t('forms.chips.shuffled'))
    return chips
}
