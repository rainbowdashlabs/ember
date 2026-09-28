/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Whether a link may be offered once a form is sent: none at all, a web address, or an address on
 * this site. The server takes nothing else, since the link is put in front of anybody who sends a
 * public form, so the editor checks the same way before it saves.
 *
 * @param link the link as typed
 */
export function isOfferableLink(link: string): boolean {
    const clean = link.trim()
    if (clean === '') return true
    const lower = clean.toLowerCase()
    return lower.startsWith('https://') || lower.startsWith('http://') || (clean.startsWith('/') && !clean.startsWith('//'))
}
