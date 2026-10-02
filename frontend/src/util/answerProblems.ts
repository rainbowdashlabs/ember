/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AnswerProblem} from '@/api/generated/schema'
import {apiErrorBody} from '@/util/apiError'
import type {Translate} from '@/util/failure'

/** The problems a refusal of answers named, one per question, or none where it named none. */
export function answerProblemsOf(e: unknown): AnswerProblem[] {
    return apiErrorBody(e)?.problems ?? []
}

/**
 * Where to show refused answers: the reader's path up to the first page with a problem, and what to
 * say at each question on it.
 *
 * <p>The server walked the same answers the browser did, so the pages it names lie on the reader's
 * path. A problem on a question the form does not have names no page, and stays on the page shown.
 *
 * @param path     the pages the reader went through, the page shown last
 * @param problems what the server refused, one entry per question
 * @param t        the translator, which has the German for every code
 * @return the path to show and the text for each question, by id
 */
export function placeProblems(
    path: readonly string[],
    problems: readonly AnswerProblem[],
    t: Translate,
): {walked: string[], marked: Record<number, string>} {
    const pagesWithProblems = new Set(problems.map(problem => problem.pageKey).filter(Boolean))
    const first = path.findIndex(key => pagesWithProblems.has(key))
    const walked = first < 0 ? [...path] : path.slice(0, first + 1)
    const marked: Record<number, string> = {}
    for (const problem of problems) {
        const key = `refusal.${problem.code}`
        const said = t(key)
        marked[problem.questionId] = said === key ? problem.message : said
    }
    return {walked, marked}
}
