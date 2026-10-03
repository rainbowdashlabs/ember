/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {Editor} from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import {TextStyle} from '@tiptap/extension-text-style'
import {Color} from '@tiptap/extension-color'
import {renderMarkdown} from '@/util/markdown'
import {createMarkdownTurndown} from './markdownTurndown'
import {placeholderTokens} from './placeholderChip'
import {extendTurndownWithTextFont, TextFont} from './textFont'
import {BlockAlign} from './blockAlign'
import {sizeAtCursor, TEXT_SIZE, TextSize} from './textSize'

/**
 * Words set in a size of their own survive the whole way a text takes in the browser: the stored markdown
 * rendered and sanitised, read by the editor, and written back as markdown, alone and together with the
 * colours, fonts, placeholders and alignment around or inside them.
 */
describe('text size', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    const tokens = placeholderTokens(new Map([['member.firstName', 'Vorname']]))

    function open(markdown: string): Editor {
        editor = new Editor({
            extensions: [StarterKit, TextStyle, Color, TextFont, TextSize, BlockAlign, ...tokens.extensions],
            content: renderMarkdown(tokens.prepare(markdown)),
        })
        return editor
    }

    function stored(opened: Editor): string {
        const turndown = createMarkdownTurndown()
        extendTurndownWithTextFont(turndown)
        tokens.extendTurndown(turndown)
        return turndown.turndown(opened.getHTML())
    }

    it('keeps the size of the words through the editor and back', () => {
        const markdown = 'Ein <span data-size="24">großes</span> Wort.'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('shows the words in their size in the editor', () => {
        expect(open('<span data-size="24">groß</span>').getHTML())
            .toMatch(/<span data-size="24" style="font-size: 24px;?">groß<\/span>/)
    })

    it('keeps bold, a colour, a font and a placeholder inside sized words', () => {
        const markdown = '<span data-size="18">Hallo {{member.firstName}}, **fett** '
            + '<span data-font="Hausschrift">in Schrift</span> und <span style="color: #ff0000">rot</span></span>'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('keeps a size inside coloured words and keeps it there once stored', () => {
        const markdown = '<span style="color: #ff0000">rot und <span data-size="30">groß</span></span>'

        const once = stored(open(markdown))
        editor?.destroy()

        expect(once).toContain('<span data-size="30">')
        expect(once).toContain('#ff0000')
        expect(stored(open(once))).toBe(once)
    })

    it('keeps a size inside an aligned paragraph', () => {
        const markdown = '<div data-align="center">\n\nEin <span data-size="12">kleines</span> Wort.\n\n</div>'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('drops a size out of bounds and keeps the words', () => {
        expect(stored(open('Ein <span data-size="500">riesiges</span> Wort.'))).toBe('Ein riesiges Wort.')
    })

    it('sets the selected words in a size and names it at the cursor', () => {
        const opened = open('Hallo Welt')
        opened.chain().setTextSelection({from: 7, to: 11}).setMark(TEXT_SIZE, {size: 20}).run()

        expect(sizeAtCursor(opened.getAttributes(TEXT_SIZE))).toBe(20)
        expect(stored(opened)).toBe('Hallo <span data-size="20">Welt</span>')
    })

    it('gives the words their normal size back', () => {
        const opened = open('<span data-size="20">Hallo Welt</span>')
        opened.chain().selectAll().unsetMark(TEXT_SIZE).run()

        expect(sizeAtCursor(opened.getAttributes(TEXT_SIZE))).toBeNull()
        expect(stored(opened)).toBe('Hallo Welt')
    })
})
