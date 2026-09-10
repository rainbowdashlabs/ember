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

/** The session facts a problem report records about its reporter. */
export interface ReportSessionContext {
    userType?: string
    permissions?: readonly string[]
}

/**
 * What a report is about where it was opened from something that failed.
 *
 * <p>Carried inside the message rather than in fields of its own: an operator reading a report wants the
 * reader's account and the server's answer together, and one text is where they read them.
 */
export interface ReportAbout {
    summary: string
    technical?: string
}

export async function submitReport(
    description: string,
    sessionInfo: ReportSessionContext | null | undefined,
    about?: ReportAbout,
): Promise<ProblemReport> {
    const roles = [sessionInfo?.userType, ...(sessionInfo?.permissions ?? [])].filter(Boolean).join(', ')
    const res = await client.post<ProblemReport>('/problem-reports', {
        message: composeMessage(description, about),
        pageUrl: window.location.href,
        userRoles: roles,
        recentRequests: JSON.stringify(getRequestHistory()),
        browserInfo: navigator.userAgent,
        screenSize: `${window.innerWidth}x${window.innerHeight}`,
    })
    return res.data
}

/**
 * The reader's own words first, because that is what an operator reads to understand the report, and
 * what the screen and the server said underneath it.
 */
function composeMessage(description: string, about?: ReportAbout): string {
    if (!about) return description
    const lines = [description, '', `Was schiefging: ${about.summary}`]
    if (about.technical) lines.push(`Serverantwort: ${about.technical}`)
    return lines.join('\n')
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
