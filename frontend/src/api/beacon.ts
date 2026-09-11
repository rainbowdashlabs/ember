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
    frames: string
    occurrences: number
    firstOccurrence: string
    lastOccurrence: string
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
    frames: string | null
    instances: number
    occurrences: number
    versions: string[]
    firstSeen: string
    lastSeen: string
    acknowledged: boolean
    resolvedIn: string | null
}

/** A forwarded report, with whoever can be written to about it. */
export interface BeaconReport {
    id: number
    message: string
    page: string | null
    version: string | null
    contactName: string | null
    contactMail: string | null
    reportedAt: string
    acknowledged: boolean
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
