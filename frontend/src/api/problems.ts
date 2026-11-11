/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {ProblemState, type ProblemContext, type ResolveResult, type StoredProblem} from './generated/schema'

/** What an administrator can do with one problem from its card. */
export type ProblemAction = 'resolve' | 'mute' | 'unmute' | 'delete' | 'send'

/** The states a list can ask for: one of them, or all at once. */
export type ProblemStateFilter = ProblemState | 'all'

/** How long a problem's title runs before it is cut, so one long log line cannot flood a card or a dialog. */
const TITLE_LIMIT = 200

/**
 * What a problem is called on its card and in the dialogs about it: the exception it threw, or the first
 * message it was logged with.
 */
export function problemTitle(entry: StoredProblem): string {
    const title = entry.exceptionClass
        ? `${entry.exceptionClass}: ${entry.exceptionMessage ?? ''}`
        : (entry.distinctMessages[0] ?? entry.message)
    return title.length > TITLE_LIMIT ? `${title.slice(0, TITLE_LIMIT)}...` : title
}

/**
 * The recorded problems in one state, or in all of them.
 *
 * @param state which state to list
 * @param refusals whether to list the refusals instead of the warnings and errors
 */
export async function listProblems(state: ProblemStateFilter = ProblemState.OPEN, refusals = false): Promise<StoredProblem[]> {
    const res = await client.get<StoredProblem[]>('/admin/problems', {params: {state, refusals}})
    return res.data
}

export async function resolve(id: number): Promise<void> {
    await client.post(`/admin/problems/${id}/resolve`)
}

export async function mute(id: number): Promise<void> {
    await client.post(`/admin/problems/${id}/mute`)
}

export async function unmute(id: number): Promise<void> {
    await client.post(`/admin/problems/${id}/unmute`)
}

export async function deleteProblem(id: number): Promise<void> {
    await client.delete(`/admin/problems/${id}`)
}

/**
 * Resolves every open problem of one kind.
 *
 * @param refusals whether to resolve the refusals instead of the warnings and errors
 */
export async function resolveAll(refusals: boolean): Promise<ResolveResult> {
    const res = await client.post<ResolveResult>('/admin/problems/resolve-all', null, {params: {refusals}})
    return res.data
}

/**
 * The stored log around one occurrence of a problem.
 *
 * @param at when the occurrence happened, or null for the newest
 * @param allThreads whether to list every thread, not only the occurrence's own
 */
export async function context(id: number, at: string | null = null, allThreads = false): Promise<ProblemContext> {
    const params = at ? {at, allThreads} : {allThreads}
    const res = await client.get<ProblemContext>(`/admin/problems/${id}/context`, {params})
    return res.data
}
