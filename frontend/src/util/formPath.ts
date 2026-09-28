/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {PageTargetKind, type PageTarget} from '@/api/forms'
import type {AnswerValue} from '@/util/formAnswers'

/** A page as the walk needs it: its key and where it leads once done. */
export interface PathPage {
    key: string
    after: PageTarget
}

/** A question as the walk needs it: which page it stands on. */
export interface PathQuestion {
    id: number
    pageKey: string
}

/** The answers given so far, by question id. */
export type PathAnswers = Record<number, AnswerValue | undefined>

function indexOf(pages: readonly PathPage[], key: string): number {
    return pages.findIndex(page => page.key === key)
}

/**
 * The page a target leads to from the page at the given position, or null where it ends the form.
 * A target naming a page that is not further down ends the form too, so no walk can ever loop.
 */
function resolve(pages: readonly PathPage[], from: number, target: PageTarget): string | null {
    if (target.kind === PageTargetKind.SUBMIT) return null
    if (target.kind === PageTargetKind.PAGE) {
        const to = target.page ? indexOf(pages, target.page) : -1
        return to > from ? pages[to]!.key : null
    }
    return pages[from + 1]?.key ?? null
}

/**
 * The page that follows the given one, or null where the form is sent after it.
 *
 * @param pages   the form's pages, in their order
 * @param pageKey the page being left
 */
export function followingPage(pages: readonly PathPage[], pageKey: string): string | null {
    const from = indexOf(pages, pageKey)
    const page = pages[from]
    if (!page) return null
    return resolve(pages, from, page.after)
}

/**
 * Every page a reader visits, from the first page to the one the form is sent from.
 *
 * @param pages the form's pages, in their order
 */
export function walkPath(pages: readonly PathPage[]): string[] {
    const path: string[] = []
    let current: string | null = pages[0]?.key ?? null
    while (current !== null && !path.includes(current)) {
        path.push(current)
        current = followingPage(pages, current)
    }
    return path
}

/**
 * Every page any target of the given page can lead to, the end of the form left out.
 *
 * @param pages the form's pages, in their order
 * @param from  the position of the page being left
 */
export function possibleNext(pages: readonly PathPage[], from: number): string[] {
    const page = pages[from]
    if (!page) return []
    const next = resolve(pages, from, page.after)
    return next === null ? [] : [next]
}

/**
 * How many pages the longest path still possible from a page holds, the page itself included.
 *
 * <p>What is left to fill in depends on answers not given yet, so a progress bar measures against the
 * most there can still be. Every page leads only further down, so this only ever shrinks from one
 * page to the next, and a bar measured against it never goes backwards.
 *
 * @param pages   the form's pages, in their order
 * @param pageKey the page to count from
 */
export function longestFrom(pages: readonly PathPage[], pageKey: string): number {
    const memo = new Map<number, number>()
    const longest = (at: number): number => {
        const known = memo.get(at)
        if (known !== undefined) return known
        const rest = possibleNext(pages, at).map(key => longest(indexOf(pages, key)))
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
 * @param pages the form's pages, in their order
 */
export function reachablePages(pages: readonly PathPage[]): Set<string> {
    const reached = new Set<string>()
    const queue = pages.length > 0 ? [0] : []
    while (queue.length > 0) {
        const at = queue.shift()!
        const key = pages[at]?.key
        if (key === undefined || reached.has(key)) continue
        reached.add(key)
        for (const next of possibleNext(pages, at)) queue.push(indexOf(pages, next))
    }
    return reached
}
