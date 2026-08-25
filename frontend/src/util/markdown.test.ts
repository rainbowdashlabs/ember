/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment jsdom
import {describe, expect, it} from 'vitest'
import {renderMarkdown, renderPageMarkdown} from './markdown'

/**
 * jsdom rather than the happy-dom the other unit tests use. happy-dom serialises nodes DOMPurify
 * has already removed back into the string it returns, so the assertions below would pass against
 * a renderer that sanitises nothing.
 */

describe('renderMarkdown', () => {
    it('renders ordinary markdown', () => {
        const html = renderMarkdown('# Title\n\nA **bold** word.')

        expect(html).toContain('<h1>Title</h1>')
        expect(html).toContain('<strong>bold</strong>')
    })

    it('drops the event handler an author wrote into the markdown', () => {
        const html = renderMarkdown('<img src=x onerror="fetch(\'https://attacker.example\')">')

        expect(html).toContain('<img')
        expect(html).not.toContain('onerror')
    })

    it('drops a script tag', () => {
        const html = renderMarkdown('before<script>alert(1)</script>after')

        expect(html).not.toContain('<script')
        expect(html).not.toContain('alert(1)')
    })

    it('drops a javascript link', () => {
        const html = renderMarkdown('[click](javascript:alert(1))')

        expect(html).not.toContain('javascript:')
    })

    it('keeps a link that goes somewhere', () => {
        const html = renderMarkdown('[docs](https://example.org/docs)')

        expect(html).toContain('href="https://example.org/docs"')
    })

    it('answers with nothing for nothing', () => {
        expect(renderMarkdown('')).toBe('')
        expect(renderMarkdown(null)).toBe('')
        expect(renderMarkdown(undefined)).toBe('')
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
