/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client, {getRequestHistory} from './client'

export interface ProblemReport {
    id: number
    stationId: string
    memberId?: number | null
    reporterName: string
    message: string
    pageUrl?: string | null
    userRoles?: string | null
    recentRequests?: string | null
    browserInfo?: string | null
    screenSize?: string | null
    acknowledged: boolean
    createdAt: string
}

export async function submitReport(message: string, sessionInfo: any): Promise<ProblemReport> {
    const roles = sessionInfo?.roles?.map((r: any) => r.name ?? r).join(', ') ?? ''
    const res = await client.post<ProblemReport>('/problem-reports', {
        message,
        pageUrl: window.location.href,
        userRoles: roles,
        recentRequests: JSON.stringify(getRequestHistory()),
        browserInfo: navigator.userAgent,
        screenSize: `${window.innerWidth}x${window.innerHeight}`,
    })
    return res.data
}

export async function listReports(includeAcknowledged = false): Promise<ProblemReport[]> {
    const res = await client.get<ProblemReport[]>('/admin/problem-reports', {
        params: includeAcknowledged ? {includeAcknowledged: 'true'} : {},
    })
    return res.data
}

export async function acknowledgeReport(id: number): Promise<void> {
    await client.post(`/admin/problem-reports/${id}/acknowledge`)
}

export async function acknowledgeAllReports(): Promise<{acknowledged: number}> {
    const res = await client.post<{acknowledged: number}>('/admin/problem-reports/acknowledge-all')
    return res.data
}

export async function deleteReport(id: number): Promise<void> {
    await client.delete(`/admin/problem-reports/${id}`)
}
