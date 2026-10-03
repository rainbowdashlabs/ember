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
import {extendTurndownWithTextFont, TEXT_FONT, TextFont} from './textFont'

/**
 * Words set in a family of their own survive the whole way a letter's text takes in the browser: the
 * stored markdown rendered and sanitised, read by the editor, and written back as markdown.
 */
describe('text font', () => {
    let editor: Editor | null = null

    afterEach(() => {
        editor?.destroy()
        editor = null
    })

    function open(markdown: string): Editor {
        editor = new Editor({extensions: [StarterKit, TextFont], content: renderMarkdown(markdown)})
        return editor
    }

    function stored(opened: Editor): string {
        const turndown = createMarkdownTurndown()
        extendTurndownWithTextFont(turndown)
        return turndown.turndown(opened.getHTML())
    }

    it('keeps the family of the words, bold among them, through the editor and back', () => {
        const markdown = 'Ein <span data-font="Berlin Type">schönes **fettes**</span> Wort.'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('keeps a family with characters HTML reserves', () => {
        const markdown = '<span data-font="Fett &amp; &quot;Breit&quot;">Wort</span>'

        expect(stored(open(markdown))).toBe(markdown)
    })

    it('sets the selected words in a family and names it on hover', () => {
        const opened = open('Hallo Welt')
        opened.chain().setTextSelection({from: 7, to: 11}).setMark(TEXT_FONT, {family: 'Hausschrift'}).run()

        expect(opened.getHTML()).toContain('title="Hausschrift"')
        expect(stored(opened)).toBe('Hallo <span data-font="Hausschrift">Welt</span>')
    })

    it('puts the words back in the template font', () => {
        const opened = open('<span data-font="Hausschrift">Hallo Welt</span>')
        opened.chain().selectAll().unsetMark(TEXT_FONT).run()

        expect(stored(opened)).toBe('Hallo Welt')
    })
})
