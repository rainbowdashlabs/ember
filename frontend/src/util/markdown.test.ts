/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment jsdom */
import {describe, expect, it} from 'vitest'
import {markdownSnippet, renderPageMarkdown} from './markdown'
import {describeMarkdownRendering} from '@/test/markdownSanitizing'

/**
 * jsdom rather than the happy-dom the other unit tests use. happy-dom serialises nodes DOMPurify
 * has already removed back into the string it returns, so the assertions below would pass against
 * a renderer that sanitises nothing.
 */

describeMarkdownRendering('in the browser')

describe('markdownSnippet', () => {
    it('says what the markdown says, without the markdown', () => {
        expect(markdownSnippet('**Treffpunkt** am *Gerätehaus*')).toBe('Treffpunkt am Gerätehaus')
    })

    it('reads a heading and a list as the words they are', () => {
        expect(markdownSnippet('# Übung\n\n- Schlauch\n- Leiter')).toBe('Übung Schlauch Leiter')
    })

    it('cuts a long description at a word', () => {
        const long = `${'wort '.repeat(60)}ende`

        const snippet = markdownSnippet(long, 40)

        expect(snippet.length).toBeLessThanOrEqual(41)
        expect(snippet.endsWith('…')).toBe(true)
        expect(snippet).not.toContain('wor…')
    })

    it('hands back text rather than markup, so nothing an organiser wrote can act', () => {
        expect(markdownSnippet('<img src=x onerror="alert(1)"> hallo')).not.toContain('<')
    })

    it('answers with nothing for nothing', () => {
        expect(markdownSnippet('')).toBe('')
        expect(markdownSnippet(null)).toBe('')
        expect(markdownSnippet(undefined)).toBe('')
    })
})

describe('renderPageMarkdown', () => {
    it('points an embedded page image at the width the endpoint serves', () => {
        const html = renderPageMarkdown('![alt](/api/v1/public/pages/abc/files/deadbeef)')

        expect(html).toContain('w=1024')
        expect(html).toContain('loading="lazy"')
    })

    it('sanitises before it touches the images', () => {
        const html = renderPageMarkdown('<img src="/api/v1/public/pages/abc/files/deadbeef" onerror="alert(1)">')

        expect(html).not.toContain('onerror')
    })
})
