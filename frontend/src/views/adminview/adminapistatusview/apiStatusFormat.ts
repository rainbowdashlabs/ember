/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** A duration in whole milliseconds, with anything under one written as such. */
export function formatMs(ms: number): string {
    return ms < 1 ? '<1ms' : Math.round(ms) + 'ms'
}

/** A share between zero and one as a percentage with one decimal. */
export function formatPercent(rate: number): string {
    return (rate * 100).toFixed(1) + '%'
}

/** The colour an HTTP method is written in, so reads and writes tell apart at a glance. */
export function methodColor(method: string): string {
    switch (method) {
        case 'GET': return 'text-green-600'
        case 'POST': return 'text-blue-600'
        case 'PUT': return 'text-yellow-600'
        case 'DELETE': return 'text-red-600'
        default: return 'text-(--text-muted)'
    }
}
