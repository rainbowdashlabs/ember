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
 * The opening words of some markdown, as plain text.
 *
 * <p>For a row in a list, where the description is a hint at what the entry is rather than the entry
 * itself. Printed as markdown source it showed its own asterisks and hashes; rendered in full it
 * turned a row into a page. The text is what markdown was going to say, cut at a word.
 *
 * <p>It comes back as text, never as markup, so the caller prints it and nothing of what an organiser
 * wrote can act as HTML. Where there is no browser to read the rendering with, the source is
 * collapsed instead, which reads the same for the plain descriptions that make up nearly all of them.
 *
 * @param markdown what was written, or nothing
 * @param limit    how many characters to keep before cutting
 * @return the text, cut with an ellipsis where it was longer
 */
export function markdownSnippet(markdown: string | null | undefined, limit = 160): string {
    if (!markdown) return ''
    const text = plainText(markdown)
    if (text.length <= limit) return text
    const cut = text.slice(0, limit)
    const lastSpace = cut.lastIndexOf(' ')
    return `${(lastSpace > limit / 2 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`
}

function plainText(markdown: string): string {
    if (typeof window === 'undefined') return markdown.replace(/\s+/g, ' ').trim()
    const holder = document.createElement('div')
    holder.innerHTML = renderMarkdown(markdown)
    return (holder.textContent ?? '').replace(/\s+/g, ' ').trim()
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
