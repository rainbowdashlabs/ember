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
import {BlockAlign} from './blockAlign'
import {
    applyLetterSpacing,
    applyLineHeight,
    BlockLineHeight,
    LETTER_SPACINGS,
    letterSpacingAtCursor,
    LINE_HEIGHTS,
    lineHeightAtCursor,
    TextLetterSpacing,
} from './textSpacing'

/**
 * Letter spacing on words and line height on paragraphs survive the whole way a text takes in the
 * browser: the stored markdown rendered and sanitised, read by the editor, and written back as markdown.
 */
describe('text spacing', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    function open(markdown: string): Editor {
        editor = new Editor({
            extensions: [StarterKit, TextStyle, Color, BlockAlign, TextLetterSpacing, BlockLineHeight],
            content: renderMarkdown(markdown),
        })
        return editor
    }

    function stored(opened: Editor): string {
        return createMarkdownTurndown().turndown(opened.getHTML())
    }

    it.each(LETTER_SPACINGS)('keeps words spaced %s through the editor and back', spacing => {
        const markdown = `Ein <span data-spacing="${spacing}">**weites**</span> Wort.`

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('drops a spacing the editor never writes and keeps the words', () => {
        expect(stored(open('Ein <span data-spacing="huge">weites</span> Wort.'))).toBe('Ein weites Wort.')
    })

    it('spaces the word at the cursor and takes the spacing off again', () => {
        const opened = open('Hallo Welt')
        opened.commands.setTextSelection(9)

        applyLetterSpacing(opened, 'wide')
        expect(letterSpacingAtCursor(opened)).toBe('wide')
        expect(stored(opened)).toBe('Hallo <span data-spacing="wide">Welt</span>')

        applyLetterSpacing(opened, null)
        expect(stored(opened)).toBe('Hallo Welt')
    })

    it.each(LINE_HEIGHTS)('keeps a paragraph with a line height of %s through the editor and back', lineHeight => {
        const markdown = `<div data-line-height="${lineHeight}">\n\nEin Absatz.\n\n</div>`

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('keeps an alignment and a line height in one wrapper', () => {
        const markdown = '<div data-align="center" data-line-height="1.5">\n\n## Titel\n\n</div>'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('sets the line height of the paragraph at the cursor and takes it back to normal', () => {
        const opened = open('Erster\n\nZweiter')
        opened.commands.setTextSelection(2)

        applyLineHeight(opened, '2')
        expect(lineHeightAtCursor(opened)).toBe('2')
        expect(stored(opened)).toBe('<div data-line-height="2">\n\nErster\n\n</div>\n\nZweiter')

        applyLineHeight(opened, null)
        expect(stored(opened)).toBe('Erster\n\nZweiter')
    })
})
