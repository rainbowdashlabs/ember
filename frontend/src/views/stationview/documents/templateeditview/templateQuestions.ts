/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    DateKind,
    PlaceholderCategory,
    TemplateQuestionKind,
    type Placeholder,
    type TemplateQuestion,
} from '@/api/generated/schema'

/** What a template writes before the key of a question to name its answer. */
export const ANSWER_PREFIX = 'ask.'

/** The longest key of a question, as the server takes it. */
const MAX_KEY = 40

/**
 * The answers of a template's questions as placeholders the picker offers, below the category the
 * picker calls by the word given. A date answer takes the date formats like any other date.
 *
 * @param questions the questions of the template
 * @param category  what the picker calls the category of the answers
 */
export function answerPlaceholders(questions: readonly TemplateQuestion[], category: string): Placeholder[] {
    return questions
        .filter(question => question.key.trim() !== '' && question.label.trim() !== '')
        .map(question => ({
            key: ANSWER_PREFIX + question.key,
            label: question.label,
            category: PlaceholderCategory.QUESTION,
            path: [category, question.label],
            informal: false,
            eventOnly: false,
            dateKind: question.kind === TemplateQuestionKind.DATE ? DateKind.DATE : null,
        }))
}

/**
 * A key for a question made from its label: lower case letters, digits and underscores, umlauts
 * written out, counted on where another question has it.
 *
 * @param label the label of the question
 * @param taken the keys the other questions have
 */
export function keyFromLabel(label: string, taken: readonly string[]): string {
    const base = label.toLowerCase()
        .replaceAll('ä', 'ae').replaceAll('ö', 'oe').replaceAll('ü', 'ue').replaceAll('ß', 'ss')
        .normalize('NFKD')
        .replace(/[^a-z0-9]+/g, '_')
        .replace(/^_+|_+$/g, '')
        .slice(0, MAX_KEY - 4)
        .replace(/_+$/, '') || 'abfrage'
    let key = base
    for (let count = 2; taken.includes(key); count++) key = `${base}_${count}`
    return key
}

/**
 * A new question of a kind, asked as written, named by a key no other question has.
 *
 * @param kind      what it takes
 * @param questions the questions the template asks already
 */
export function newQuestion(kind: TemplateQuestionKind, questions: readonly TemplateQuestion[]): TemplateQuestion {
    return {
        key: keyFromLabel('', questions.map(question => question.key)),
        label: '',
        kind,
        suggestion: '',
        required: false,
    }
}
