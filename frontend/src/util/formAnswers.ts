/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuestionTypes, type QuestionType} from '@/api/forms'
import type {FormAnswerValue} from '@/api/generated/schema'
import {optionsOf} from '@/util/formOptions'

/**
 * The answer a question starts with, in the shape the server reads, so an unanswered question is
 * empty rather than absent. A ranking starts in the order its options are shown, since that is what
 * the reader sees before they move anything.
 *
 * @param type   the kind of question
 * @param config the question's settings, which hold a ranking's options
 */
export function emptyAnswer(type: QuestionType, config: Record<string, unknown>): FormAnswerValue {
    switch (type) {
        case QuestionTypes.CHOICE: return {type, selected: [], other: ''}
        case QuestionTypes.TEXT: return {type, text: ''}
        case QuestionTypes.RATING: return {type, rating: 0}
        case QuestionTypes.DATE: return {type, date: ''}
        case QuestionTypes.RANKING: return {type, order: optionsOf(config).map(option => option.key)}
        case QuestionTypes.LIKERT: return {type, ratings: {}}
    }
}

/**
 * An answer as it was stored, read back for the question it answers. The stored value names the kind
 * of question where it was kept with one; a value that cannot be read, or that answers another kind of
 * question than the question now is, starts empty instead.
 *
 * @param type   the kind of question
 * @param config the question's settings
 * @param stored the answer as the server keeps it
 */
export function storedAnswer(type: QuestionType, config: Record<string, unknown>, stored: string): FormAnswerValue {
    try {
        const parsed = JSON.parse(stored)
        if (typeof parsed === 'object' && parsed !== null && (parsed.type ?? type) === type) {
            const answer: FormAnswerValue = {...parsed, type}
            return answer
        }
    } catch {
        void 0
    }
    return emptyAnswer(type, config)
}

function blank(value: string | undefined): boolean {
    return value === undefined || value.trim() === ''
}

/**
 * Whether an answer says nothing: no option and no text picked, no text written, no star given, no
 * date set, nothing ranked or no statement rated. The server reads answers the same way, so what is
 * empty here is what it strips before it checks and stores.
 *
 * @param answer the answer, possibly none at all
 */
export function isEmptyAnswer(answer: FormAnswerValue | undefined): boolean {
    switch (answer?.type) {
        case QuestionTypes.CHOICE: return answer.selected.length === 0 && blank(answer.other)
        case QuestionTypes.TEXT: return blank(answer.text)
        case QuestionTypes.RATING: return answer.rating < 1
        case QuestionTypes.DATE: return blank(answer.date)
        case QuestionTypes.RANKING: return answer.order.length === 0
        case QuestionTypes.LIKERT: return Object.keys(answer.ratings).length === 0
        default: return true
    }
}
