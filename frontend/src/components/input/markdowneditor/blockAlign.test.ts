/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {Editor} from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import {Table} from '@tiptap/extension-table'
import {TableRow} from '@tiptap/extension-table-row'
import {TableHeader} from '@tiptap/extension-table-header'
import {TableCell} from '@tiptap/extension-table-cell'
import {TextStyle} from '@tiptap/extension-text-style'
import {Color} from '@tiptap/extension-color'
import {renderMarkdown} from '@/util/markdown'
import {createMarkdownTurndown} from './markdownTurndown'
import {placeholderTokens} from './placeholderChip'
import {extendTurndownWithTextFont, TextFont} from './textFont'
import {alignBlock, BlockAlign, canAlign, isAligned, STORED_ALIGNMENTS} from './blockAlign'

/**
 * Centred, right-aligned and justified paragraphs and headings survive the whole way a text takes in the
 * browser: the stored markdown rendered and sanitised, read by the editor, and written back as markdown.
 */
describe('block alignment', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    const tokens = placeholderTokens(new Map([['member.firstName', 'Vorname']]))

    function open(markdown: string): Editor {
        editor = new Editor({
            extensions: [
                StarterKit, Table, TableRow, TableHeader, TableCell, TextStyle, Color, TextFont, BlockAlign,
                ...tokens.extensions,
            ],
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

    it.each(STORED_ALIGNMENTS)('keeps a %s paragraph through the editor and back', alignment => {
        const markdown = `<div data-align="${alignment}">\n\nEin **fetter** Absatz.\n\n</div>`

        const opened = open(markdown)

        expect(isAligned(opened, alignment)).toBe(true)
        expect(stored(opened)).toBe(markdown)
    })

    it.each(STORED_ALIGNMENTS)('keeps a %s heading through the editor and back', alignment => {
        const markdown = `<div data-align="${alignment}">\n\n## Überschrift\n\n</div>`

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('keeps a placeholder, a font and a colour inside an aligned paragraph', () => {
        const markdown = '<div data-align="center">\n\n'
            + 'Hallo {{member.firstName}}, <span data-font="Hausschrift">in **Schrift**</span> '
            + 'und <span style="color: #ff0000">rot</span>.\n\n</div>'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('leaves an unaligned paragraph plain markdown', () => {
        const opened = open('Ein Absatz.')

        expect(isAligned(opened, 'left')).toBe(true)
        expect(stored(opened)).toBe('Ein Absatz.')
    })

    it('aligns the paragraph at the cursor and takes it back to the left', () => {
        const opened = open('Erster\n\nZweiter')
        opened.commands.setTextSelection(2)

        alignBlock(opened, 'right')
        expect(stored(opened)).toBe('<div data-align="right">\n\nErster\n\n</div>\n\nZweiter')

        alignBlock(opened, 'left')
        expect(stored(opened)).toBe('Erster\n\nZweiter')
    })

    it('keeps the alignment of a paragraph in a list item', () => {
        const opened = open('- Erster Punkt\n- Zweiter Punkt')
        opened.commands.setTextSelection(4)
        alignBlock(opened, 'center')
        const markdown = stored(opened)
        opened.destroy()

        const reopened = open(markdown)
        reopened.commands.setTextSelection(4)

        expect(isAligned(reopened, 'center')).toBe(true)
        expect(stored(reopened)).toBe(markdown)
    })

    it('aligns nothing in a table cell, where the alignment could not be stored', () => {
        const opened = open('| Kopf |\n| --- |\n| Zelle |')
        opened.commands.setTextSelection(4)

        expect(canAlign(opened)).toBe(false)
        expect(alignBlock(opened, 'center')).toBe(false)
    })
})

/** What the web pages show: the sanitiser keeps the alignment and nothing more than it kept before. */
describe('rendering an aligned block', () => {
    it.each(STORED_ALIGNMENTS)('keeps the %s alignment around the rendered markdown', alignment => {
        const html = renderMarkdown(`<div data-align="${alignment}">\n\n## Titel mit **fett**\n\n</div>`)

        expect(html).toContain(`<div data-align="${alignment}">`)
        expect(html).toContain('<strong>fett</strong>')
    })

    it('strips what is dangerous on the aligned block, as anywhere else', () => {
        const html = renderMarkdown('<div data-align="center" onclick="alert(1)">\n\nText\n\n</div>\n\n<script>alert(2)</script>')

        expect(html).toContain('data-align="center"')
        expect(html).not.toContain('onclick')
        expect(html).not.toContain('<script')
    })
})
