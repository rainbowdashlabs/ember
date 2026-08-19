/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** The severities the log knows, coarsest last so a filter reads in the order people think in. */
export const LOG_LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'] as const

export type LogLevel = (typeof LOG_LEVELS)[number]

/** One line of the application log. */
export interface LogEntry {
    id: number
    loggedAt: string
    level: string
    logger: string
    thread: string
    message: string
    throwable: string | null
}

/** A page of the log, with what a reader needs to make sense of a short one. */
export interface ApplicationLogPage {
    entries: LogEntry[]
    /** Whether anything is being stored at all. A short log usually means this is off. */
    databaseEnabled: boolean
    databaseLevel: string
    retentionDays: number
    /** Lines dropped since start because the queue was full, so a gap reads as a gap. */
    dropped: number
}

/** What is kept, and for how long. */
export interface LoggingConfig {
    databaseEnabled: boolean
    databaseLevel: string
    retentionDays: number
    /** How many lines are stored, so a retention change can be judged before it is made. */
    storedLines?: number
}

export interface LogQuery {
    levels?: string[]
    search?: string
    /** Read further back than this line. */
    before?: number
    limit?: number
}

export async function searchLog(query: LogQuery = {}): Promise<ApplicationLogPage> {
    const res = await client.get<ApplicationLogPage>('/admin/monitoring/log', {
        params: {
            level: query.levels?.length ? query.levels.join(',') : undefined,
            search: query.search || undefined,
            before: query.before,
            limit: query.limit,
        },
    })
    return res.data
}

/** Empties the stored log, for when it holds something that should not be kept. */
export async function clearLog(): Promise<void> {
    await client.delete('/admin/monitoring/log')
}

export async function getLoggingConfig(): Promise<LoggingConfig> {
    const res = await client.get<LoggingConfig>('/admin/config/logging')
    return res.data
}

export async function updateLoggingConfig(config: LoggingConfig): Promise<LoggingConfig> {
    const res = await client.put<LoggingConfig>('/admin/config/logging', {
        databaseEnabled: config.databaseEnabled,
        databaseLevel: config.databaseLevel,
        retentionDays: config.retentionDays,
    })
    return res.data
}
