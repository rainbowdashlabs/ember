/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {renderPageMarkdown} from './markdown'
import {describeMarkdownRendering} from '@/test/markdownSanitizing'

/**
 * Markdown rendered where a server render runs it: plain Node, with no window of its own.
 *
 * <p>A public page is read by crawlers and link previews in the form the server sends, so the
 * formatting has to be there already, and cleaned exactly as the browser would clean it.
 *
 * @vitest-environment node
 */
describe('markdown on the server', () => {
    it('runs without a window', () => {
        expect(typeof window).toBe('undefined')
    })

    it('renders a public page cell rather than escaping its source', () => {
        const html = renderPageMarkdown('**Treffpunkt** am Gerätehaus')

        expect(html).toContain('<strong>Treffpunkt</strong>')
        expect(html).not.toContain('**')
    })
})

describeMarkdownRendering('on the server')
