/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * A short text with a placeholder put at its end, a space before it where the text does not already
 * end in one. Used where a plain text field takes placeholders: a letterhead cell, a field on a PDF, a
 * form field of a PDF.
 *
 * @param text the text so far
 * @param key  the placeholder's key
 */
export function withPlaceholder(text: string, key: string): string {
    const gap = text.length === 0 || /\s$/.test(text) ? '' : ' '
    return `${text}${gap}{{${key}}}`
}
