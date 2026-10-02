/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {PageTargetKind} from '@/api/forms'
import {FormQuestionType, type FormAnswerValue, type PageTarget} from '@/api/generated/schema'

/** A page as the walk needs it: its key and where it leads once done. */
export interface PathPage {
    key: string
    after: PageTarget
}

/**
 * A question as the walk needs it: which page it stands on and, for the one question of a page that
 * decides where the page leads, the page that follows per option picked.
 */
export interface PathQuestion {
    id: number
    pageKey: string
    branch?: Record<string, PageTarget> | null
}

/** The answers given so far, by question id. */
export type PathAnswers = Record<number, FormAnswerValue | undefined>

function indexOf(pages: readonly PathPage[], key: string | null | undefined): number {
    return key ? pages.findIndex(page => page.key === key) : -1
}

/**
 * The page a target leads to from the page at the given position, or null where it ends the form.
 * A target naming a page that is not further down ends the form too, so no walk can ever loop.
 */
function resolve(pages: readonly PathPage[], from: number, target: PageTarget): string | null {
    if (target.kind === PageTargetKind.SUBMIT) return null
    if (target.kind === PageTargetKind.PAGE) {
        const to = indexOf(pages, target.page)
        return to > from ? pages[to]!.key : null
    }
    return pages[from + 1]?.key ?? null
}

/** The one question of a page that decides where it leads, if it has one. */
function decidingQuestion(questions: readonly PathQuestion[], pageKey: string): PathQuestion | undefined {
    return questions.find(question => question.pageKey === pageKey && question.branch)
}

/** The single option picked in a choice answer, which is the only answer a page can branch on. */
function pickedOption(answer: FormAnswerValue | undefined): string | null {
    if (answer?.type !== FormQuestionType.CHOICE) return null
    return answer.selected.length === 1 ? (answer.selected[0] ?? null) : null
}

/**
 * Where a page leads with the given answers: where its deciding question's answer says, and where the
 * page itself says otherwise. The server walks the same way when the form is sent.
 */
function targetOf(page: PathPage, questions: readonly PathQuestion[], answers: PathAnswers): PageTarget {
    const deciding = decidingQuestion(questions, page.key)
    const picked = deciding ? pickedOption(answers[deciding.id]) : null
    return (picked !== null ? deciding?.branch?.[picked] : undefined) ?? page.after
}

/**
 * The page that follows the given one, or null where the form is sent after it.
 *
 * @param pages     the form's pages, in their order
 * @param questions the form's questions
 * @param answers   the answers given so far
 * @param pageKey   the page being left
 */
export function followingPage(
    pages: readonly PathPage[],
    questions: readonly PathQuestion[],
    answers: PathAnswers,
    pageKey: string,
): string | null {
    const from = indexOf(pages, pageKey)
    const page = pages[from]
    if (!page) return null
    return resolve(pages, from, targetOf(page, questions, answers))
}

/**
 * Every page a reader visits with the given answers, from the first page to the one the form is sent
 * from.
 *
 * @param pages     the form's pages, in their order
 * @param questions the form's questions
 * @param answers   the answers given
 */
export function walkPath(pages: readonly PathPage[], questions: readonly PathQuestion[], answers: PathAnswers): string[] {
    const path: string[] = []
    let current: string | null = pages[0]?.key ?? null
    while (current !== null && !path.includes(current)) {
        path.push(current)
        current = followingPage(pages, questions, answers, current)
    }
    return path
}

/**
 * Every page the page at the given position can lead to, whatever is answered, the end of the form
 * left out.
 *
 * @param pages     the form's pages, in their order
 * @param questions the form's questions
 * @param from      the position of the page being left
 */
export function possibleNext(pages: readonly PathPage[], questions: readonly PathQuestion[], from: number): string[] {
    const page = pages[from]
    if (!page) return []
    const targets = [page.after, ...Object.values(decidingQuestion(questions, page.key)?.branch ?? {})]
    const next = targets.map(target => resolve(pages, from, target)).filter((key): key is string => key !== null)
    return [...new Set(next)]
}

/**
 * How many pages the longest path still possible from a page holds, the page itself included.
 *
 * <p>What is left to fill in depends on answers not given yet, so a progress bar measures against the
 * most there can still be. Every page leads only further down, so this only ever shrinks from one
 * page to the next, and a bar measured against it never goes backwards.
 *
 * @param pages     the form's pages, in their order
 * @param questions the form's questions
 * @param pageKey   the page to count from
 */
export function longestFrom(pages: readonly PathPage[], questions: readonly PathQuestion[], pageKey: string): number {
    const memo = new Map<number, number>()
    const longest = (at: number): number => {
        const known = memo.get(at)
        if (known !== undefined) return known
        const rest = possibleNext(pages, questions, at).map(key => longest(indexOf(pages, key)))
        const length = 1 + Math.max(0, ...rest)
        memo.set(at, length)
        return length
    }
    const start = indexOf(pages, pageKey)
    return start < 0 ? 0 : longest(start)
}

/**
 * The pages some path reaches, whatever is answered. A page missing here is one nobody will see.
 *
 * @param pages     the form's pages, in their order
 * @param questions the form's questions
 */
export function reachablePages(pages: readonly PathPage[], questions: readonly PathQuestion[]): Set<string> {
    const reached = new Set<string>()
    const queue = pages.length > 0 ? [0] : []
    while (queue.length > 0) {
        const at = queue.shift()!
        const key = pages[at]?.key
        if (key === undefined || reached.has(key)) continue
        reached.add(key)
        for (const next of possibleNext(pages, questions, at)) queue.push(indexOf(pages, next))
    }
    return reached
}
