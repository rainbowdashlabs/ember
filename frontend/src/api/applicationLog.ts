/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ApplicationLogPage, LogFacet, LoggingConfig, LoggingConfigRequest} from './generated/schema'

/** The severities the log knows, coarsest last so a filter reads in the order people think in. */
export const LOG_LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'] as const

export type LogLevel = (typeof LOG_LEVELS)[number]

export interface LogQuery {
    levels?: string[]
    search?: string
    /** Only lines from this logger. */
    logger?: string
    /** Only lines from threads with this name once the numbering is taken out. */
    thread?: string
    /** Read further back than this line. */
    before?: number
    limit?: number
}

/** A page of the log, with what a reader needs to make sense of a short one. */
export async function searchLog(query: LogQuery = {}): Promise<ApplicationLogPage> {
    const res = await client.get<ApplicationLogPage>('/admin/monitoring/log', {
        params: {
            level: query.levels?.length ? query.levels.join(',') : undefined,
            search: query.search || undefined,
            logger: query.logger || undefined,
            thread: query.thread || undefined,
            before: query.before,
            limit: query.limit,
        },
    })
    return res.data
}

/**
 * Searches the loggers or threads the current filter matches.
 *
 * The page carries the busiest of them already; this reaches the ones below that cut-off, and
 * costs nothing more than the list when nothing is typed.
 */
export async function searchFacets(
    kind: 'logger' | 'thread',
    name: string,
    query: LogQuery = {},
): Promise<LogFacet[]> {
    const res = await client.get<LogFacet[]>('/admin/monitoring/log/facets', {
        params: {
            kind,
            name: name || undefined,
            level: query.levels?.length ? query.levels.join(',') : undefined,
            search: query.search || undefined,
            logger: kind === 'thread' ? query.logger || undefined : undefined,
            thread: kind === 'logger' ? query.thread || undefined : undefined,
        },
    })
    return res.data
}

/** Empties the stored log, for when it holds something that should not be kept. */
export async function clearLog(): Promise<void> {
    await client.delete('/admin/monitoring/log')
}

/** What is kept, for how long, and how many lines that is now. */
export async function getLoggingConfig(): Promise<LoggingConfig> {
    const res = await client.get<LoggingConfig>('/admin/config/logging')
    return res.data
}

export async function updateLoggingConfig(config: LoggingConfigRequest): Promise<LoggingConfig> {
    const res = await client.put<LoggingConfig>('/admin/config/logging', {
        databaseEnabled: config.databaseEnabled,
        databaseLevel: config.databaseLevel,
        retentionDays: config.retentionDays,
    })
    return res.data
}
