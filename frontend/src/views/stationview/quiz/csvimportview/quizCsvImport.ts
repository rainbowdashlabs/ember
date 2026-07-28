/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {QuizQuestionTypeName} from '@/api/quiz'
import {QuizQuestionTypes} from '@/api/quiz'

/** Column assignment shared by the quiz CSV importers. */
export interface QuizCsvMapping {
    questionColumn: string
    answerColumn: string
    categoryColumn: string
    typeColumn: string
    pointsColumn: string
    answerSeparator: string
    defaultType: QuizQuestionTypeName
}

/** A single CSV row turned into an editable question draft. */
export interface ImportQuestion {
    title: string
    answer: string
    category: string
    type: QuizQuestionTypeName
    points: number
    included: boolean
    answerSeparator: string
    mcCorrectIndices: Set<number>
    mcPointsPerCorrect: number
    enumRequiredCount: number
    enumOrderRequired: boolean
    splitItems: string[]
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

const SPLIT_TYPES: QuizQuestionTypeName[] = [
    QuizQuestionTypes.MULTIPLE_CHOICE,
    QuizQuestionTypes.ORDERING,
    QuizQuestionTypes.FILL_IN_THE_BLANK,
    QuizQuestionTypes.ENUMERATION,
]

const TRUE_VALUES = ['true', 'wahr', 'ja', '1']

export function createQuizCsvMapping(headers: string[]): QuizCsvMapping {
    return {
        questionColumn: headers[0] ?? '',
        answerColumn: headers[1] ?? '',
        categoryColumn: '',
        typeColumn: '',
        pointsColumn: '',
        answerSeparator: ';',
        defaultType: QuizQuestionTypes.MULTIPLE_CHOICE,
    }
}

/** Maps a free form type cell (``MC``, ``Wahr/Falsch``, ``ordering``, …) onto a question type. */
export function parseQuizType(value: string, fallback: QuizQuestionTypeName): QuizQuestionTypeName {
    const normalized = value.trim().toUpperCase().replace(/[\s-]/g, '_')
    if (normalized === 'MC' || normalized.includes('MULTIPLE')) return QuizQuestionTypes.MULTIPLE_CHOICE
    if (normalized === 'TF' || normalized.includes('TRUE') || normalized.includes('WAHR')) return QuizQuestionTypes.TRUE_FALSE
    if (normalized.includes('FREE') || normalized.includes('FREI')) return QuizQuestionTypes.FREE_ANSWER
    if (normalized.includes('FILL') || normalized.includes('LÜCKE') || normalized.includes('LUECKE')) return QuizQuestionTypes.FILL_IN_THE_BLANK
    if (normalized.includes('ORDER') || normalized.includes('REIHEN')) return QuizQuestionTypes.ORDERING
    if (normalized.includes('CONNECT') || normalized.includes('ZUORDN')) return QuizQuestionTypes.CONNECT
    return fallback
}

export function needsSplit(type: QuizQuestionTypeName): boolean {
    return SPLIT_TYPES.includes(type)
}

export function splitAnswer(question: ImportQuestion): string[] {
    return question.answer.split(question.answerSeparator).map(part => part.trim()).filter(Boolean)
}

/** Turns the parsed rows into question drafts the preview step lets the user refine. */
export function buildImportQuestions(headers: string[], rows: string[][], mapping: QuizCsvMapping): ImportQuestion[] {
    function cell(row: string[], column: string): string {
        if (!column) return ''
        const index = headers.indexOf(column)
        return index >= 0 ? row[index] ?? '' : ''
    }

    return rows.map(row => {
        const title = cell(row, mapping.questionColumn)
        const typeValue = cell(row, mapping.typeColumn)
        const pointsValue = cell(row, mapping.pointsColumn)
        const question: ImportQuestion = {
            title,
            answer: cell(row, mapping.answerColumn),
            category: cell(row, mapping.categoryColumn),
            type: typeValue ? parseQuizType(typeValue, mapping.defaultType) : mapping.defaultType,
            points: pointsValue ? parseInt(pointsValue) || 1 : 1,
            included: title.trim().length > 0,
            answerSeparator: mapping.answerSeparator,
            mcCorrectIndices: new Set<number>(),
            mcPointsPerCorrect: 1,
            enumRequiredCount: 3,
            enumOrderRequired: false,
            splitItems: [],
        }
        question.splitItems = needsSplit(question.type) ? splitAnswer(question) : []
        return question
    })
}

/** Serializes a question draft into the backend question config for its type. */
export function buildQuestionConfig(question: ImportQuestion): string {
    const parts = question.splitItems.length > 0 ? question.splitItems : [question.answer]
    switch (question.type) {
        case QuizQuestionTypes.MULTIPLE_CHOICE: {
            const allCorrect = question.mcCorrectIndices.size === 0
            const options = parts.map((text, index) => ({text, correct: allCorrect || question.mcCorrectIndices.has(index)}))
            return JSON.stringify({options, pointsPerCorrect: question.mcPointsPerCorrect})
        }
        case QuizQuestionTypes.TRUE_FALSE:
            return JSON.stringify({correctAnswer: TRUE_VALUES.includes(question.answer.trim().toLowerCase())})
        case QuizQuestionTypes.FREE_ANSWER:
            return JSON.stringify({lines: 3, answers: question.answer ? [question.answer] : []})
        case QuizQuestionTypes.FILL_IN_THE_BLANK:
            return JSON.stringify({text: question.title, answers: parts})
        case QuizQuestionTypes.ORDERING:
            return JSON.stringify({items: parts})
        case QuizQuestionTypes.ENUMERATION:
            return JSON.stringify({
                answers: parts,
                requiredCount: question.enumRequiredCount,
                orderedRequired: question.enumOrderRequired,
            })
        default:
            return '{}'
    }
}
