/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client, {getRequestHistory} from './client'
import {createCrudResource} from './crud'

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
    const roles = [sessionInfo?.userType, ...(sessionInfo?.permissions ?? [])].filter(Boolean).join(', ')
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

const reports = createCrudResource<ProblemReport>('/admin/problem-reports')

export async function listReports(includeAcknowledged = false): Promise<ProblemReport[]> {
    return reports.list({includeAcknowledged: includeAcknowledged ? 'true' : undefined})
}

export async function acknowledgeReport(id: number): Promise<void> {
    await client.post(`/admin/problem-reports/${id}/acknowledge`)
}

export async function acknowledgeAllReports(): Promise<{acknowledged: number}> {
    const res = await client.post<{acknowledged: number}>('/admin/problem-reports/acknowledge-all')
    return res.data
}

export const deleteReport = reports.remove
