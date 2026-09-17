/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * What this instance sends to a beacon, and whether it is one.
 *
 * <p>Stored settings, so changing one takes effect on the next entry rather than on the next
 * restart. Everything that reads them asks at the moment it matters.
 */
export interface BeaconStatus {
    enabled: boolean
    url: string
    forwardProblems: boolean
    forwardReports: boolean
    /** Whether a report carrying a picture waits for somebody here before it is passed on. */
    reviewReportPictures: boolean
    metricsEnabled: boolean
    receiving: boolean
    contactName: string
    contactMail: string
}

/** What one fault would travel as, shown before anything leaves the instance. */
export interface ProblemPayload {
    version: string
    contactName: string | null
    contactMail: string | null
    fingerprint: string
    level: string
    logger: string
    exceptionClass: string | null
    message: string | null
    frames: string
    occurrences: number
    firstOccurrence: string
    lastOccurrence: string
}

/** What a problem report travels as: what somebody wrote, without anything naming them. */
export interface ReportPayload {
    version: string
    contactName: string | null
    contactMail: string | null
    message: string
    page: string | null
    browser: string | null
    screenSize: string | null
    roles: string | null
    recentRequests: string | null
    reportedAt: string
}

/** One subject's bucketed counts, as they would travel. */
export interface MetricsSubject {
    metricsUid: string
    subject: string
    members: string | null
    accounts: string | null
    stations: string | null
    inventory: string | null
}

/** A whole day for the instance and its stations, in one body. */
export interface MetricsBatch {
    protocolVersion: number
    version: string
    day: string
    subjects: MetricsSubject[]
}

/** A fault as a beacon has gathered it, across every instance that met it. */
export interface BeaconFault {
    id: number
    fingerprint: string
    level: string
    exceptionClass: string | null
    logger: string | null
    /** What it was logged with. For a warning without an exception it is the only thing that names it. */
    message: string | null
    frames: string | null
    instances: number
    occurrences: number
    versions: string[]
    firstSeen: string
    lastSeen: string
    acknowledged: boolean
    resolvedIn: string | null
}

/** A forwarded report, with the screen it was written about and whoever can be written to about it. */
export interface BeaconReport {
    id: number
    message: string
    page: string | null
    version: string | null
    browser: string | null
    screenSize: string | null
    /** What the reporter was allowed to do, which tells a fault some people meet from one everybody meets. */
    roles: string | null
    /** The calls the screen made before it was written, each stripped of its query string. */
    recentRequests: string | null
    contactName: string | null
    contactMail: string | null
    reportedAt: string
    acknowledged: boolean
    /** The picture the report was forwarded with, absent where it carried none. */
    screenshotFileId: number | null
    /** Which installation sent it, worked out from the key that signed the delivery. */
    instanceId: string | null
}

/** A day of one subject's bucketed counts. */
export interface BeaconMetricsRow {
    metricsUid: string
    subject: string
    day: string
    members: string | null
    accounts: string | null
    stations: string | null
    inventory: string | null
}

export async function getStatus(): Promise<BeaconStatus> {
    return (await client.get<BeaconStatus>('/admin/beacon')).data
}

/** Writes the switches and the contact, and gives back what is now stored. */
export async function updateSettings(settings: BeaconStatus): Promise<BeaconStatus> {
    return (await client.put<BeaconStatus>('/admin/beacon', settings)).data
}

/** The exact payload a problem would travel as. Nothing is sent by asking. */
export async function previewProblem(id: number): Promise<ProblemPayload> {
    return (await client.get<ProblemPayload>(`/admin/beacon/problems/${id}/preview`)).data
}

export async function sendProblem(id: number): Promise<number> {
    return (await client.post<{queued: number}>(`/admin/beacon/problems/${id}/send`)).data.queued
}

export async function sendProblems(ids: number[]): Promise<number> {
    return (await client.post<{queued: number}>('/admin/beacon/problems/send', {ids})).data.queued
}

/** What one problem report would travel as. A report is a person talking, so the bytes are shown. */
export async function previewReportPayload(id: number): Promise<ReportPayload> {
    return (await client.get<ReportPayload>(`/admin/beacon/reports/${id}/preview`)).data
}

/**
 * Passes one problem report on now.
 *
 * <p>Whatever the automatic switch says: the switch governs what leaves on its own, and this is an
 * operator deciding about the report in front of them.
 */
export async function sendReportToBeacon(id: number, picture?: ReportPictureDecision): Promise<number> {
    const answer = await client.post<{queued: number}>(`/admin/beacon/reports/${id}/send`, picture ?? {})
    return answer.data.queued
}

/**
 * What an operator decided about the picture of the report they are passing on.
 *
 * <p>A report and its picture travel together or the picture does not travel: there is no sending it
 * afterwards, which is why this is decided here and only here.
 */
export interface ReportPictureDecision {
    /** A further covered copy to send in place of the reporter's, or null to send theirs as it is. */
    screenshot?: string | null
    /** Whether it goes without a picture at all. */
    dropScreenshot?: boolean
}

/** The day's numbers as they would go, so an operator can see what leaves. */
export async function previewMetrics(): Promise<MetricsBatch> {
    return (await client.get<MetricsBatch>('/admin/beacon/figures/preview')).data
}

export async function listFaults(includeAcknowledged = false): Promise<BeaconFault[]> {
    return (await client.get<BeaconFault[]>('/admin/beacon/collected/faults', {params: {includeAcknowledged}})).data
}

export async function resolveFault(id: number, acknowledged: boolean, resolvedIn: string | null): Promise<void> {
    await client.put(`/admin/beacon/collected/faults/${id}`, {acknowledged, resolvedIn})
}

export async function listBeaconReports(includeAcknowledged = false): Promise<BeaconReport[]> {
    return (await client.get<BeaconReport[]>('/admin/beacon/collected/reports', {params: {includeAcknowledged}})).data
}

export async function acknowledgeBeaconReport(id: number): Promise<void> {
    await client.post(`/admin/beacon/collected/reports/${id}/acknowledge`)
}

export async function listBeaconMetrics(days = 30): Promise<BeaconMetricsRow[]> {
    return (await client.get<BeaconMetricsRow[]>('/admin/beacon/collected/figures', {params: {days}})).data
}
