/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AcknowledgeResult, ProblemSnapshot} from './generated/schema'

/**
 * Whether an entry holds anything beyond the line already on the card: a stacktrace, or more messages
 * than the one shown. With neither, expanding it opens an empty box, so the card does not offer it.
 */
export function hasDetails(entry: ProblemSnapshot): boolean {
    return !!entry.stacktrace || entry.distinctMessages.length > 1
}

export async function listProblems(includeAcknowledged = false): Promise<ProblemSnapshot[]> {
    const res = await client.get<ProblemSnapshot[]>('/admin/problems', {
        params: includeAcknowledged ? {includeAcknowledged: 'true'} : {},
    })
    return res.data
}

export async function acknowledge(id: number): Promise<void> {
    await client.post(`/admin/problems/${id}/acknowledge`)
}

export async function acknowledgeAll(): Promise<AcknowledgeResult> {
    const res = await client.post<AcknowledgeResult>('/admin/problems/acknowledge-all')
    return res.data
}
