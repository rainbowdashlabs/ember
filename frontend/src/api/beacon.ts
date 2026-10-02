/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    BeaconFault,
    BeaconMetricsRow,
    BeaconReport,
    BeaconSettingsRequest,
    BeaconStatus,
    MetricsBatch,
    ProblemPayload,
    ReportPayload,
    SendReportRequest,
    SendResult,
} from './generated/schema'

/**
 * What this instance sends to a beacon, and whether it is one.
 *
 * <p>Stored settings, so changing one takes effect on the next entry rather than on the next
 * restart. Everything that reads them asks at the moment it matters.
 */
export async function getStatus(): Promise<BeaconStatus> {
    return (await client.get<BeaconStatus>('/admin/beacon')).data
}

/** Writes the switches and the contact, and gives back what is now stored. */
export async function updateSettings(settings: BeaconSettingsRequest): Promise<BeaconStatus> {
    return (await client.put<BeaconStatus>('/admin/beacon', settings)).data
}

/** The exact payload a problem would travel as. Nothing is sent by asking. */
export async function previewProblem(id: number): Promise<ProblemPayload> {
    return (await client.get<ProblemPayload>(`/admin/beacon/problems/${id}/preview`)).data
}

export async function sendProblem(id: number): Promise<number> {
    return (await client.post<SendResult>(`/admin/beacon/problems/${id}/send`)).data.queued
}

export async function sendProblems(ids: number[]): Promise<number> {
    return (await client.post<SendResult>('/admin/beacon/problems/send', {ids})).data.queued
}

/** What one problem report would travel as. A report is a person talking, so the bytes are shown. */
export async function previewReportPayload(id: number): Promise<ReportPayload> {
    return (await client.get<ReportPayload>(`/admin/beacon/reports/${id}/preview`)).data
}

/**
 * Passes one problem report on now, with what an operator decided about its picture.
 *
 * <p>Whatever the automatic switch says: the switch governs what leaves on its own, and this is an
 * operator deciding about the report in front of them. A report and its picture travel together or the
 * picture does not travel: there is no sending it afterwards, which is why that is decided here and only
 * here.
 */
export async function sendReportToBeacon(id: number, picture?: SendReportRequest): Promise<number> {
    const answer = await client.post<SendResult>(`/admin/beacon/reports/${id}/send`, picture ?? {})
    return answer.data.queued
}

/** The day's numbers as they would go, so an operator can see what leaves. */
export async function previewMetrics(): Promise<MetricsBatch> {
    return (await client.get<MetricsBatch>('/admin/beacon/figures/preview')).data
}

/** The faults this beacon gathered, across every instance that met them. */
export async function listFaults(includeAcknowledged = false): Promise<BeaconFault[]> {
    return (await client.get<BeaconFault[]>('/admin/beacon/collected/faults', {params: {includeAcknowledged}})).data
}

export async function resolveFault(id: number, acknowledged: boolean, resolvedIn: string | null): Promise<void> {
    await client.put(`/admin/beacon/collected/faults/${id}`, {acknowledged, resolvedIn})
}

/** The forwarded reports, with the screen each was written about and whoever can be written to about it. */
export async function listBeaconReports(includeAcknowledged = false): Promise<BeaconReport[]> {
    return (await client.get<BeaconReport[]>('/admin/beacon/collected/reports', {params: {includeAcknowledged}})).data
}

export async function acknowledgeBeaconReport(id: number): Promise<void> {
    await client.post(`/admin/beacon/collected/reports/${id}/acknowledge`)
}

export async function listBeaconMetrics(days = 30): Promise<BeaconMetricsRow[]> {
    return (await client.get<BeaconMetricsRow[]>('/admin/beacon/collected/figures', {params: {days}})).data
}
