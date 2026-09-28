/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuestionTypes} from '@/api/forms'
import {optionsOf} from '@/util/formOptions'

/**
 * An answer as the fill screens hold it: a plain record whose shape follows from the kind of question
 * it answers.
 */
export type AnswerValue = Record<string, unknown>

/**
 * The answer a question starts with, in the shape the server reads, so an unanswered question is
 * empty rather than absent. A ranking starts in the order its options are shown, since that is what
 * the reader sees before they move anything.
 *
 * @param type   the kind of question
 * @param config the question's settings, which hold a ranking's options
 */
export function emptyAnswer(type: string, config: Record<string, unknown>): AnswerValue {
    switch (type) {
        case QuestionTypes.CHOICE: return {selected: [] as string[], other: ''}
        case QuestionTypes.TEXT: return {text: ''}
        case QuestionTypes.RATING: return {rating: 0}
        case QuestionTypes.DATE: return {date: ''}
        case QuestionTypes.RANKING: return {order: optionsOf(config).map(option => option.key)}
        case QuestionTypes.LIKERT: return {ratings: {}}
        default: return {}
    }
}

function blank(value: unknown): boolean {
    return typeof value !== 'string' || value.trim() === ''
}

function none(value: unknown): boolean {
    return !Array.isArray(value) || value.length === 0
}

/**
 * The answers as the server reads them: each marked with the kind of question it answers, which is how
 * the server tells one shape from another.
 *
 * @param questions the questions answered
 * @param answers   the answers, by question id
 * @param typeOf    what kind each question is
 */
export function typedAnswers<Q extends {id: number}>(
    questions: readonly Q[],
    answers: Record<number, AnswerValue | undefined>,
    typeOf: (question: Q) => string,
): Record<number, AnswerValue> {
    const typed: Record<number, AnswerValue> = {}
    for (const question of questions) {
        const value = answers[question.id]
        if (value !== undefined) typed[question.id] = {type: typeOf(question), ...value}
    }
    return typed
}

/**
 * Whether an answer says nothing: no option and no text picked, no text written, no star given, no
 * date set, nothing ranked or no statement rated. The server reads answers the same way, so what is
 * empty here is what it strips before it checks and stores.
 *
 * @param type  the kind of question
 * @param value the answer, possibly none at all
 */
export function isEmptyAnswer(type: string, value: AnswerValue | undefined): boolean {
    if (!value) return true
    switch (type) {
        case QuestionTypes.CHOICE: return none(value.selected) && blank(value.other)
        case QuestionTypes.TEXT: return blank(value.text)
        case QuestionTypes.RATING: return typeof value.rating !== 'number' || value.rating < 1
        case QuestionTypes.DATE: return blank(value.date)
        case QuestionTypes.RANKING: return none(value.order)
        case QuestionTypes.LIKERT: return Object.keys((value.ratings as object | undefined) ?? {}).length === 0
        default: return true
    }
}
