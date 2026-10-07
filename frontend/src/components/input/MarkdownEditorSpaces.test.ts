/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {VueWrapper} from '@vue/test-utils'
import {nextTick} from 'vue'
import type {Editor} from '@tiptap/vue-3'
import MarkdownEditor from './MarkdownEditor.vue'
import EditorToolbar from './markdowneditor/EditorToolbar.vue'

const NBSP = String.fromCodePoint(0xa0)

/**
 * Several spaces typed in a row are meant, and they survive being saved and opened again.
 *
 * <p>Markdown, HTML and Pandoc all fold a run of spaces into one, so every space of a run but the last
 * is stored as a non-breaking space, which none of them folds. In the editor they are ordinary spaces
 * again, so they are typed over and deleted like any other.
 */
describe('MarkdownEditor spaces in a row', () => {
    let mounted: VueWrapper | null = null

    afterEach(() => {
        mounted?.unmount()
        mounted = null
    })

    async function opened(markdown: string) {
        mounted?.unmount()
        let stored = markdown
        mounted = await mountSuspended(MarkdownEditor, {
            attachTo: document.body,
            props: {modelValue: markdown, 'onUpdate:modelValue': (value: string) => { stored = value }},
        })
        await settled()
        const editor = mounted.findComponent(EditorToolbar).props('editor') as Editor
        return {editor, stored: () => stored}
    }

    /** The editor hands its state on two animation frames after a change. */
    async function settled() {
        for (let frame = 0; frame < 2; frame++) await new Promise(resolve => requestAnimationFrame(resolve))
        await nextTick()
    }

    async function typed(editor: Editor, text: string) {
        editor.view.dispatch(editor.state.tr.insertText(text))
        await settled()
    }

    async function storedAfterTyping(text: string) {
        const {editor, stored} = await opened('')
        await typed(editor, text)
        return stored()
    }

    it('stores two spaces in a row as a non-breaking space and a space', async () => {
        expect(await storedAfterTyping('Name:  Anna')).toBe(`Name:${NBSP} Anna`)
    })

    it('stores three spaces in a row as two non-breaking spaces and a space', async () => {
        expect(await storedAfterTyping('Name:   Anna')).toBe(`Name:${NBSP}${NBSP} Anna`)
    })

    it('stores the spaces a line starts with', async () => {
        expect(await storedAfterTyping('   eingerückt')).toBe(`${NBSP}${NBSP} eingerückt`)
    })

    it('leaves single spaces as they are', async () => {
        expect(await storedAfterTyping('ein ganz normaler Satz')).toBe('ein ganz normaler Satz')
    })

    it.each([
        ['two spaces', 'Name:  Anna'],
        ['three spaces', 'Name:   Anna'],
        ['leading spaces', '   eingerückt'],
    ])('opens %s again as the spaces that were typed', async (_name, text) => {
        const stored = await storedAfterTyping(text)
        const {editor, stored: storedAgain} = await opened(stored)

        expect(editor.state.doc.textContent).toBe(text)
        await typed(editor, '!')
        expect(storedAgain()).toBe(`${stored}!`)
    })

    it('keeps spaces in a row beside bold, coloured and sized words', async () => {
        const markdown = `**fett**${NBSP} <span style="color: #ff0000">rot${NBSP}${NBSP} rot</span>${NBSP} `
            + `<span style="font-size: 24px">groß</span>`
        const {editor, stored} = await opened(markdown)

        expect(editor.state.doc.textContent).toBe('fett  rot   rot  groß')
        editor.commands.setTextSelection(1)
        await typed(editor, '!')
        expect(stored()).toBe(markdown.replace('**fett**', '**!fett**'))
    })

    it('leaves a line break as it is', async () => {
        const {editor, stored} = await opened('eins  \nzwei')
        editor.commands.setTextSelection(1)
        await typed(editor, '!')

        expect(stored()).toBe('!eins  \nzwei')
    })

    it('keeps spaces inside code as plain spaces', async () => {
        const {editor, stored} = await opened('`a  b`')

        expect(editor.state.doc.textContent).toBe('a  b')
        editor.commands.setTextSelection(1)
        await typed(editor, 'x')
        expect(stored()).toBe('`xa  b`')
    })
})
