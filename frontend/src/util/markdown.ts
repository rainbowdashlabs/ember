/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import DOMPurify from 'dompurify'
import {marked} from 'marked'
import {enhancePageMarkdownImages} from '@/util/pageMarkdownImages'

/**
 * The one way markdown becomes HTML in this application.
 *
 * `marked` dropped its own sanitising in version 5 and passes raw HTML through by design, so the
 * caller has to clean what it produces. The markdown here is written by members, page editors,
 * ticket authors and event organisers, and some of it is shown to unauthenticated visitors, so an
 * unsanitised path is stored cross-site scripting against everyone who opens the page.
 *
 * A server render has no DOM to clean with. There the markdown is escaped instead of parsed:
 * losing the formatting is visible and recoverable, letting unsanitised HTML into the response is
 * neither.
 */
export function renderMarkdown(markdown: string | null | undefined): string {
    if (!markdown) return ''
    if (typeof window === 'undefined') return escape(markdown)
    try {
        return DOMPurify.sanitize(marked.parse(markdown, {async: false}))
    } catch {
        return escape(markdown)
    }
}

/**
 * Markdown for a page, with the images it embeds pointed at the width the page files endpoint
 * serves. The rewrite runs after sanitising, so it only ever sees tags that survived it.
 */
export function renderPageMarkdown(markdown: string | null | undefined): string {
    const html = renderMarkdown(markdown)
    return html ? enhancePageMarkdownImages(html) : html
}

/** Text a browser shows verbatim, for the paths that have no DOM to sanitise with. */
function escape(text: string): string {
    return text
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;')
}
