/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {Editor} from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import {renderMarkdown} from '@/util/markdown'
import {createMarkdownTurndown} from './markdownTurndown'
import {openEmptyLines, withoutOuterEmptyLines} from './emptyLines'

const NBSP = String.fromCodePoint(0xa0)

/** Lines left empty on purpose survive the editor, the stored markdown and the rendered page. */
describe('empty lines', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    function open(markdown: string): Editor {
        editor = new Editor({extensions: [StarterKit], content: openEmptyLines(renderMarkdown(markdown))})
        return editor
    }

    function stored(opened: Editor): string {
        return withoutOuterEmptyLines(createMarkdownTurndown().turndown(opened.getHTML()))
    }

    it('keeps empty paragraphs typed between two lines', () => {
        const opened = new Editor({extensions: [StarterKit], content: '<p>Oben</p><p></p><p></p><p>Unten</p>'})
        editor = opened

        expect(stored(opened)).toBe(`Oben\n\n${NBSP}\n\n${NBSP}\n\nUnten`)
    })

    it('opens a stored empty line as an empty paragraph and stores it the same way again', () => {
        const markdown = `Oben\n\n${NBSP}\n\nUnten`
        const opened = open(markdown)

        expect(opened.getHTML()).toBe('<p>Oben</p><p></p><p>Unten</p>')
        expect(stored(opened)).toBe(markdown)
    })

    it('shows a stored empty line on the page', () => {
        expect(renderMarkdown(`Oben\n\n${NBSP}\n\nUnten`)).toContain('<p>&nbsp;</p>')
    })

    it('stores no empty line before the first or after the last line', () => {
        const opened = new Editor({extensions: [StarterKit], content: '<p></p><p>Text</p><p></p><p></p>'})
        editor = opened

        expect(stored(opened)).toBe('Text')
    })
})
