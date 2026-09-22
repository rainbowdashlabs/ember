/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {QuestionTypes, type FormQuestionInfo, type FormQuestionTally, type FormResultGroup} from '@/api/forms'

/**
 * A question's counts laid out for comparing groups: one row per option or statement, one column
 * per group. The grouped chart and the table view both draw from it, so the two cannot disagree.
 *
 * <p>Groups are rarely the same size, so choices and ratings are compared as the share of each
 * group's answers rather than as raw counts. Likert grids compare the average rating, rankings the
 * average points per answer.
 */
export interface ResultMatrix {
    rows: string[]
    unit: 'percent' | 'average'
    /** The value per row and group; null where a group has nothing to show for that row. */
    values: (number | null)[][]
    /** The answers behind each value, for the tooltip and the table. */
    counts: number[][]
    /** The number of answers each group gave to the question. */
    answers: number[]
}

/** A question's configuration, which arrives either parsed or as the JSON it is stored as. */
export function questionConfig(config: Record<string, unknown> | string): Record<string, unknown> {
    if (typeof config === 'object' && config !== null) return config
    try { return JSON.parse(config || '{}') } catch { return {} }
}

/** The tally of one question within one group. */
export function tallyIn(group: FormResultGroup, questionId: number): FormQuestionTally | undefined {
    return group.tallies.find(tally => tally.questionId === questionId)
}

function share(count: number, of: number): number | null {
    return of > 0 ? Math.round(count / of * 1000) / 10 : null
}

/**
 * Lays out one question's counts across the groups, or null for a question whose answers are
 * listed rather than counted.
 *
 * @param otherLabel what the row of free "other" answers to a choice is called
 */
export function matrixOf(question: FormQuestionInfo, groups: FormResultGroup[], otherLabel: string): ResultMatrix | null {
    const cfg = questionConfig(question.config)
    const tallies = groups.map(group => tallyIn(group, question.questionId))
    const answers = tallies.map(tally => tally?.answerCount ?? 0)
    const percentOf = (countsPerGroup: number[][], rows: string[]): ResultMatrix => ({
        rows,
        unit: 'percent',
        counts: countsPerGroup,
        values: countsPerGroup.map(row => row.map((count, g) => share(count, answers[g] ?? 0))),
        answers,
    })

    switch (question.questionType) {
        case QuestionTypes.CHOICE: {
            const options = (cfg.options as string[]) || []
            const rows = options.map((_, i) => tallies.map(t => t?.optionCounts?.[i] ?? 0))
            const labels = [...options]
            if (cfg.allowOther) {
                rows.push(tallies.map(t => t?.otherCount ?? 0))
                labels.push(otherLabel)
            }
            return percentOf(rows, labels)
        }
        case QuestionTypes.RATING: {
            const scale = Math.max(0, ...tallies.map(t => t?.ratingCounts?.length ?? 0))
            const rows = Array.from({length: scale}, (_, i) => tallies.map(t => t?.ratingCounts?.[i] ?? 0))
            return percentOf(rows, rows.map((_, i) => String(i + 1)))
        }
        case QuestionTypes.LIKERT: {
            const statements = (cfg.statements as string[]) || []
            return {
                rows: statements.map((statement, i) => statement || `Option ${i + 1}`),
                unit: 'average',
                values: statements.map((_, i) => tallies.map(t => t?.statementAverages?.[i] ?? null)),
                counts: statements.map(() => answers),
                answers,
            }
        }
        case QuestionTypes.RANKING: {
            const options = (cfg.options as string[]) || []
            return {
                rows: options,
                unit: 'average',
                values: options.map((_, i) => tallies.map((t, g) => {
                    const of = answers[g] ?? 0
                    return of > 0 ? Math.round((t?.rankingScores?.[i] ?? 0) / of * 10) / 10 : null
                })),
                counts: options.map(() => answers),
                answers,
            }
        }
        default:
            return null
    }
}

/** The average rating a group gave, or null where it gave none. */
export function averageRating(tally: FormQuestionTally | undefined): number | null {
    const counts = tally?.ratingCounts ?? []
    const total = counts.reduce((sum, count) => sum + count, 0)
    if (total === 0) return null
    const points = counts.reduce((sum, count, i) => sum + count * (i + 1), 0)
    return Math.round(points / total * 10) / 10
}

/** A value as a reader reads it: "42,5 %" for a share, "4,2" for an average. */
export function formatValue(value: number | null, unit: ResultMatrix['unit'], locale: string): string {
    if (value === null) return '-'
    const number = value.toLocaleString(locale, {maximumFractionDigits: 1})
    return unit === 'percent' ? `${number} %` : number
}
