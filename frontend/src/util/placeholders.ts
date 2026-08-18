/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** Matches `{{ name }}` with optional surrounding whitespace, mirroring the backend pattern. */
const PLACEHOLDER = /\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g

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
