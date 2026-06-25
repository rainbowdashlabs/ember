/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
function pad2(n: number): string {
    return String(n).padStart(2, '0')
}

/**
 * Formats an ISO timestamp as `HH:mm`, returning an empty string when the input is missing.
 * Uses the runtime's local time zone, which is the project default for time-only displays.
 */
export function formatTime(iso?: string | null): string {
    if (!iso) return ''
    const d = new Date(iso)
    return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/**
 * Formats an ISO timestamp as `dd.MM.yyyy` for display. Returns an empty string when the input
 * is missing. Use this for date-only output where the locale's German `de-DE` format is wanted
 * without time components.
 */
export function formatDate(iso?: string | null): string {
    if (!iso) return ''
    return new Date(iso).toLocaleDateString('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
    })
}

/**
 * Formats an ISO timestamp as `dd.MM.yyyy, HH:mm` for display. Returns an empty string when the
 * input is missing. Mirrors the most common date+time display used across views.
 */
export function formatDateTime(iso?: string | null): string {
    if (!iso) return ''
    return new Date(iso).toLocaleDateString('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
    })
}
