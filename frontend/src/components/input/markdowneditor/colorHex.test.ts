/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {Editor} from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import {Highlight} from '@tiptap/extension-highlight'
import {TextStyle} from '@tiptap/extension-text-style'
import {Color} from '@tiptap/extension-color'
import {renderMarkdown} from '@/util/markdown'
import {createMarkdownTurndown} from './markdownTurndown'
import {asHex, hexColor} from './colorHex'

describe('hex codes', () => {
    it.each([
        ['#1a2b3c', '#1a2b3c'],
        ['#1A2B3C', '#1a2b3c'],
        ['1a2b3c', '#1a2b3c'],
        ['#abc', '#aabbcc'],
        ['  #ABC ', '#aabbcc'],
    ])('reads %s as %s', (typed, color) => {
        expect(hexColor(typed)).toBe(color)
    })

    it.each(['', '#', '#12', '#1234', '#12345', '#1234567', '#ggg', 'red', 'rgb(1, 2, 3)', '#abc;color:red'])(
        'refuses %s', typed => {
            expect(hexColor(typed)).toBeNull()
        })

    it('reads a colour the browser hands back as rgb as its hex code', () => {
        expect(asHex('rgb(26, 43, 60)')).toBe('#1a2b3c')
        expect(asHex('rgba(255, 0, 0, 0.5)')).toBe('#ff0000')
        expect(asHex('#ABC')).toBe('#aabbcc')
        expect(asHex('red')).toBeNull()
        expect(asHex(null)).toBeNull()
    })
})

/**
 * Any colour, not only a swatch, survives the way a text takes in the browser: the stored markdown
 * rendered and sanitised, read by the editor, and written back as markdown.
 */
describe('colours of the editor', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    function open(markdown: string): Editor {
        editor = new Editor({
            extensions: [StarterKit, TextStyle, Color, Highlight.configure({multicolor: true})],
            content: renderMarkdown(markdown),
        })
        return editor
    }

    function stored(opened: Editor): string {
        return createMarkdownTurndown().turndown(opened.getHTML())
    }

    it('keeps any text colour through the editor and back', () => {
        const markdown = 'Ein <span style="color: #1a2b3c">farbiges</span> Wort.'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('keeps any highlight colour through the editor and back', () => {
        const markdown = 'Ein <mark data-color="#a1b2c3" style="background-color: #a1b2c3">markiertes</mark> Wort.'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('stores a colour set from a hex code as that hex code', () => {
        const opened = open('Hallo Welt')
        opened.chain().setTextSelection({from: 1, to: 6}).setColor('#0f0f0f').run()
        opened.chain().setTextSelection({from: 7, to: 11}).setHighlight({color: '#123456'}).run()

        expect(stored(opened)).toBe('<span style="color: #0f0f0f">Hallo</span> '
            + '<mark data-color="#123456" style="background-color: #123456">Welt</mark>')
    })

    it('stores a colour written as rgb by an earlier version as its hex code', () => {
        expect(stored(open('<span style="color: rgb(26, 43, 60)">Wort</span>')))
            .toBe('<span style="color: #1a2b3c">Wort</span>')
    })

    it('keeps the default highlight as markdown', () => {
        expect(stored(open('Ein ==markiertes== Wort.'))).toBe('Ein ==markiertes== Wort.')
    })
})
