/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface ProblemEntry {
    id: number
    level: string
    logger: string
    exceptionClass: string | null
    exceptionMessage: string | null
    stacktrace: string | null
    firstOccurrence: string
    lastOccurrence: string
    count: number
    acknowledged: boolean
    distinctMessages: string[]
}

export async function listProblems(includeAcknowledged = false): Promise<ProblemEntry[]> {
    const res = await client.get<ProblemEntry[]>('/admin/problems', {
        params: includeAcknowledged ? {includeAcknowledged: 'true'} : {},
    })
    return res.data
}

export async function acknowledge(id: number): Promise<void> {
    await client.post(`/admin/problems/${id}/acknowledge`)
}

export async function acknowledgeAll(): Promise<{acknowledged: number}> {
    const res = await client.post<{acknowledged: number}>('/admin/problems/acknowledge-all')
    return res.data
}
