/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuizQuestionTypes, type CsvMappings, type QuizCatalogExportQuestion, type QuizQuestionTypeName} from '@/api/quiz'

/**
 * A drafted question as the wizard holds it: what would be created, the answer cell it was read
 * from, and whether it is still going in. Reading a cell into a config happens on the way in, so
 * everything here edits a config that already has its shape.
 */
export interface ImportDraft {
    question: QuizCatalogExportQuestion
    rawAnswer: string
    answerSeparator: string
    included: boolean
}

export const QUIZ_CSV_TYPES: QuizQuestionTypeName[] = [
    QuizQuestionTypes.MULTIPLE_CHOICE,
    QuizQuestionTypes.TRUE_FALSE,
    QuizQuestionTypes.FREE_ANSWER,
    QuizQuestionTypes.FILL_IN_THE_BLANK,
    QuizQuestionTypes.ORDERING,
    QuizQuestionTypes.ENUMERATION,
    QuizQuestionTypes.CONNECT,
]

export const ANSWER_SEPARATOR_PRESETS: {label: string; value: string}[] = [
    {label: ';', value: ';'},
    {label: ',', value: ','},
    {label: '\\n', value: '\n'},
    {label: '␣', value: ' '},
]

export function createQuizCsvMapping(headers: string[]): CsvMappings {
    return {
        questionColumn: headers[0] ?? '',
        answerColumn: headers[1] ?? '',
        categoryColumn: '',
        typeColumn: '',
        pointsColumn: '',
        descriptionColumn: '',
        imageColumn: '',
        distractorColumn: '',
        pointsPerCorrectColumn: '',
        requiredCountColumn: '',
        orderedRequiredColumn: '',
        separator: ',',
        answerSeparator: ';',
        defaultType: QuizQuestionTypes.MULTIPLE_CHOICE,
    }
}

type OptionConfig = {options?: {text: string; correct: boolean}[]}
type PairConfig = {pairs?: {left: string; right: string}[]}
type ListConfig = {items?: string[]; answers?: string[]}

/** Whether a question of this type keeps its answers as a list the wizard can edit. */
export function hasAnswerList(type: QuizQuestionTypeName): boolean {
    return type !== QuizQuestionTypes.TRUE_FALSE && type !== QuizQuestionTypes.IMAGE_TEXT
}

/** Whether the wizard offers to mark single entries right or wrong. */
export function hasCorrectness(type: QuizQuestionTypeName): boolean {
    return type === QuizQuestionTypes.MULTIPLE_CHOICE
}

/** The answers of a drafted config, whichever field its type keeps them in. */
export function answerList(draft: ImportDraft): string[] {
    const config = draft.question.config
    switch (draft.question.quizQuestionType) {
        case QuizQuestionTypes.MULTIPLE_CHOICE:
            return ((config as OptionConfig).options ?? []).map(option => option.text)
        case QuizQuestionTypes.CONNECT:
            return ((config as PairConfig).pairs ?? []).map(pair => `${pair.left}=${pair.right}`)
        case QuizQuestionTypes.ORDERING:
            return (config as ListConfig).items ?? []
        default:
            return (config as ListConfig).answers ?? []
    }
}

/** Whether the entry at this position counts as a right answer. */
export function isCorrect(draft: ImportDraft, index: number): boolean {
    return (draft.question.config as OptionConfig).options?.[index]?.correct ?? false
}

/**
 * Writes an edited answer list back into the config, keeping what the wizard cannot show. A
 * multiple-choice option keeps whether it was right where it stays in place, so renaming one does
 * not silently make it wrong.
 */
export function setAnswerList(draft: ImportDraft, answers: string[]) {
    const config = draft.question.config
    switch (draft.question.quizQuestionType) {
        case QuizQuestionTypes.MULTIPLE_CHOICE: {
            const previous = (config as OptionConfig).options ?? []
            ;(config as OptionConfig).options = answers.map((text, index) => ({
                text,
                correct: previous[index]?.correct ?? index === 0,
            }))
            return
        }
        case QuizQuestionTypes.CONNECT: {
            ;(config as PairConfig).pairs = answers.map(entry => {
                const [left, right] = entry.split('=', 2)
                return {left: (left ?? '').trim(), right: (right ?? '').trim()}
            })
            return
        }
        case QuizQuestionTypes.ORDERING:
            ;(config as ListConfig).items = answers
            return
        default:
            ;(config as ListConfig).answers = answers
    }
}

export function toggleCorrect(draft: ImportDraft, index: number) {
    const option = (draft.question.config as OptionConfig).options?.[index]
    if (option) option.correct = !option.correct
}

/** Splits the answer cell again on a different separator, for a sheet with mixed punctuation. */
export function resplitAnswers(draft: ImportDraft) {
    const parts = draft.rawAnswer
        .split(draft.answerSeparator)
        .map(part => part.trim())
        .filter(Boolean)
    setAnswerList(draft, parts)
}
