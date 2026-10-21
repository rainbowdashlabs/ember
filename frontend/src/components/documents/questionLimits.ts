/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    TemplateQuestionKind,
    TextFormat,
    type DayBound,
    type QuestionLimits,
    type TemplateQuestion,
} from '@/api/generated/schema'

/** Limits that limit nothing, which a new question and a question of a changed kind start from. */
export const NO_LIMITS: QuestionLimits = {
    earliest: null,
    latest: null,
    maxLength: null,
    format: null,
    min: null,
    max: null,
    whole: false,
    choices: [],
}

/** What is wrong with an answer, named as the form words it. */
export type AnswerProblem =
    | 'notADay'
    | 'dayOutOfRange'
    | 'tooLong'
    | 'wrongFormat'
    | 'notANumber'
    | 'numberOutOfRange'
    | 'notAChoice'

const FORMATS: Record<TextFormat, RegExp> = {
    [TextFormat.DIGITS]: /^\d+$/,
    [TextFormat.LETTERS]: /^\p{L}+(?:[ '-]+\p{L}+)*$/u,
    [TextFormat.POSTAL_CODE]: /^\d{5}$/,
    [TextFormat.EMAIL]: /^[^@\s]+@[^@\s]+\.[^@\s]+$/,
    [TextFormat.PHONE]: /^\+?\d[\d /-]*$/,
}

const NUMBER = /^-?\d{1,15}(?:[.,]\d{1,6})?$/
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/

/**
 * The day a bound names, as an ISO date.
 *
 * @param bound a bound of a date question, or null
 * @param today the day of generation, as an ISO date
 */
export function dayOf(bound: DayBound | null, today: string): string | null {
    if (!bound) return null
    if (bound.day) return bound.day
    if (bound.fromToday === null) return null
    const day = new Date(`${today}T00:00:00Z`)
    day.setUTCDate(day.getUTCDate() + bound.fromToday)
    return day.toISOString().slice(0, 10)
}

/** Today in the reader's calendar, as an ISO date. */
export function todayIso(): string {
    const now = new Date()
    return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10)
}

/**
 * What is wrong with an answer, by the same rules the server checks it by. An empty answer is never
 * wrong here: whether it is missing is the required flag's business.
 *
 * @param question the question
 * @param answer   the answer as the form holds it
 * @param today    the day of generation, as an ISO date
 */
export function answerProblem(question: TemplateQuestion, answer: string | undefined, today: string): AnswerProblem | null {
    const written = (answer ?? '').trim()
    if (written === '') return null
    const limits = question.limits
    switch (question.kind) {
        case TemplateQuestionKind.TEXT:
            return textProblem(limits, written)
        case TemplateQuestionKind.DATE:
            return dayProblem(limits, written, today)
        case TemplateQuestionKind.NUMBER:
            return numberProblem(limits, written)
        case TemplateQuestionKind.CHOICE:
            return limits.choices.some(choice => choice.toLowerCase() === written.toLowerCase()) ? null : 'notAChoice'
        default:
            return null
    }
}

function textProblem(limits: QuestionLimits, written: string): AnswerProblem | null {
    if (limits.maxLength !== null && written.length > limits.maxLength) return 'tooLong'
    if (limits.format !== null && !FORMATS[limits.format].test(written)) return 'wrongFormat'
    return null
}

function dayProblem(limits: QuestionLimits, written: string, today: string): AnswerProblem | null {
    if (!ISO_DAY.test(written)) return 'notADay'
    const earliest = dayOf(limits.earliest, today)
    const latest = dayOf(limits.latest, today)
    if ((earliest && written < earliest) || (latest && written > latest)) return 'dayOutOfRange'
    return null
}

function numberProblem(limits: QuestionLimits, written: string): AnswerProblem | null {
    if (!NUMBER.test(written)) return 'notANumber'
    const value = Number(written.replace(',', '.'))
    const outside = (limits.min !== null && value < limits.min)
        || (limits.max !== null && value > limits.max)
        || (limits.whole && !Number.isInteger(value))
    return outside ? 'numberOutOfRange' : null
}

/**
 * Whether any answer of a form is wrong, which keeps the document from being previewed or generated.
 *
 * @param questions the questions of the template
 * @param answers   the answers by the key of the question
 * @param today     the day of generation, as an ISO date
 */
export function anyAnswerProblem(questions: readonly TemplateQuestion[], answers: Record<string, string>, today: string): boolean {
    return questions.some(question => answerProblem(question, answers[question.key], today) !== null)
}
