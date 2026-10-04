/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DATE_FORMAT_MAX_LENGTH} from './dateFormatPattern'

/** Matches `{{ name }}` with optional surrounding whitespace, mirroring the backend pattern. */
const PLACEHOLDER = /\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g

/**
 * How a placeholder is written in a document template: `{{key}}`, or `{{key|format}}` for a date in a
 * format of its own, spaces inside the braces allowed. It reads what the server reads, a format up to
 * twice the length the server takes, so a mistyped one stays a placeholder and is refused by name on
 * saving.
 */
const TEMPLATE_PLACEHOLDER = new RegExp(
    String.raw`\{\{\s*([A-Za-z0-9_.]+)\s*(?:\|([^{}|"\\\r\n]{0,${DATE_FORMAT_MAX_LENGTH * 2}}))?}}`, 'g')

/** What stands between the key of a value and its format, as in `member.birthDate|long`. */
export const FORMAT_SEPARATOR = '|'

/** A key as a template writes it: the key of a value, and for a date the format after a bar. */
export interface PlaceholderKey {
    base: string
    format: string | null
}

/**
 * @param written a key as a template writes it
 * @returns the key read into the key of its value and its format
 */
export function parsePlaceholderKey(written: string): PlaceholderKey {
    const bar = written.indexOf(FORMAT_SEPARATOR)
    if (bar < 0) return {base: written, format: null}
    return {base: written.slice(0, bar), format: written.slice(bar + 1)}
}

/**
 * @param base   the key of a value
 * @param format its format, or null for none
 * @returns the key as a template writes it
 */
export function writtenPlaceholderKey(base: string, format: string | null): string {
    return format === null ? base : `${base}${FORMAT_SEPARATOR}${format}`
}

/**
 * Replaces every placeholder of a document template text.
 *
 * @param text        the text with `{{key}}` and `{{key|format}}` in it
 * @param replacement what a placeholder becomes, given its key with the format trimmed
 */
export function replaceTemplatePlaceholders(text: string, replacement: (key: string) => string): string {
    return text.replace(TEMPLATE_PLACEHOLDER, (_match, base: string, format: string | undefined) =>
        replacement(writtenPlaceholderKey(base, format?.trim() ?? null)))
}

/**
 * Replaces every placeholder that has a value, leaving the ones without a value standing so a
 * missing value stays visible. Used for the editor preview; the published document is rendered
 * by the backend.
 */
export function applyPlaceholders(text: string, values: Record<string, string>): string {
    if (!text.includes('{{')) return text
    return text.replace(PLACEHOLDER, (token, name: string) => values[name] || token)
}

/** Returns every placeholder name appearing in the given text. */
export function placeholderNames(text: string): string[] {
    return [...new Set([...text.matchAll(PLACEHOLDER)].map(match => match[1] as string))]
}
