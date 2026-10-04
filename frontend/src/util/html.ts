/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Escapes a text for HTML, attributes in double quotes included.
 *
 * <p>The editor writes stored markdown with it, and the server and the print filter read exactly these
 * four entities back, so it escapes no further character.
 */
export function escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/** Reads a text back as {@link escapeHtml} wrote it. */
export function unescapeHtml(text: string): string {
    return text.replace(/&quot;/g, '"').replace(/&gt;/g, '>').replace(/&lt;/g, '<').replace(/&amp;/g, '&')
}
