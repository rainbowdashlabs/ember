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
 * is missing or is not a date at all. Use this for date-only output where the locale's German
 * `de-DE` format is wanted without time components.
 */
export function formatDate(iso?: string | null): string {
    if (!iso) return ''
    const date = new Date(iso)
    if (Number.isNaN(date.getTime())) return ''
    return date.toLocaleDateString('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
    })
}

/**
 * Formats an ISO timestamp as a long German date - `27. Juli 2026` - for editorial
 * surfaces such as blog posts and release notes. Returns an empty string when the
 * input is missing.
 */
export function formatDateLong(iso?: string | null): string {
    if (!iso) return ''
    return new Date(iso).toLocaleDateString('de-DE', {
        year: 'numeric', month: 'long', day: 'numeric',
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

/**
 * Formats an ISO timestamp as a German relative time - "gerade eben", "vor 5 Min.",
 * "vor 3 Std.", "vor 2 Tagen" - falling back to the absolute date after 30 days.
 * Returns an empty string when the input is missing.
 */
export function formatRelative(iso?: string | null): string {
    if (!iso) return ''
    const diffMs = Date.now() - new Date(iso).getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return 'gerade eben'
    if (diffMin < 60) return `vor ${diffMin} Min.`
    const diffH = Math.floor(diffMin / 60)
    if (diffH < 24) return `vor ${diffH} Std.`
    const diffD = Math.floor(diffH / 24)
    if (diffD <= 30) return `vor ${diffD} Tag${diffD > 1 ? 'en' : ''}`
    return formatDate(iso)
}

/**
 * Turns what a date field holds (`yyyy-MM-dd`) into the instant an endpoint expecting a timestamp
 * will accept, which is midnight UTC on that day.
 *
 * <p>A date field and a timestamp field look interchangeable and are not: handed a bare `2026-03-17`
 * a backend parsing instants refuses the whole request, so the field silently loses whatever was
 * typed into it. Anything writing a date into a timestamp goes through here.
 *
 * @param date a calendar date, or nothing
 * @return the ISO instant, or null when there was no date
 */
export function dateToInstant(date?: string | null): string | null {
    if (!date) return null
    const parsed = new Date(`${date}T00:00:00Z`)
    return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString()
}

/**
 * The counterpart of {@link dateToInstant}: the calendar date a stored instant falls on, ready to
 * be put back into a date field. Returns an empty string when there is nothing, which is what an
 * empty date field holds.
 */
export function instantToDate(iso?: string | null): string {
    if (!iso) return ''
    const parsed = new Date(iso)
    if (Number.isNaN(parsed.getTime())) return ''
    return parsed.toISOString().slice(0, 10)
}

/**
 * Formats a byte count as a compact human-readable size (`B`, `KB`, `MB`), using one decimal
 * place for the kilobyte and megabyte ranges.
 */
export function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
