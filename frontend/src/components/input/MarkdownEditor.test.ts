/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {DOMWrapper, type VueWrapper} from '@vue/test-utils'
import {nextTick} from 'vue'
import type {Editor} from '@tiptap/vue-3'
import MarkdownEditor from './MarkdownEditor.vue'
import EditorToolbar from './markdowneditor/EditorToolbar.vue'

/**
 * Words in a size of their own show in that size inside the editor: as stored, and once a size is set
 * through the menu, on a selection and on a bare cursor standing in the words alike.
 */
describe('MarkdownEditor text size', () => {
    let mounted: VueWrapper | null = null

    afterEach(() => {
        mounted?.unmount()
        mounted = null
    })

    async function opened(markdown: string) {
        let stored = markdown
        mounted = await mountSuspended(MarkdownEditor, {
            attachTo: document.body,
            props: {modelValue: markdown, 'onUpdate:modelValue': (value: string) => { stored = value }},
        })
        await settled()
        const editor = mounted.findComponent(EditorToolbar).props('editor') as Editor
        return {wrapper: mounted, editor, stored: () => stored}
    }

    /** The editor hands its state to the menu two animation frames after a change. */
    async function settled() {
        for (let frame = 0; frame < 2; frame++) await new Promise(resolve => requestAnimationFrame(resolve))
        await nextTick()
    }

    async function select(editor: Editor, range: number | {from: number; to: number}) {
        editor.commands.setTextSelection(range)
        await settled()
    }

    async function pickSize(wrapper: VueWrapper, size: string) {
        await wrapper.find('[data-testid="editor-size"]').trigger('click')
        const field = new DOMWrapper(document.querySelector<HTMLInputElement>('[data-testid="editor-size-field"]')!)
        await field.setValue(size)
        await field.trigger('keydown', {key: 'Enter'})
        await settled()
    }

    function shownSizes(wrapper: VueWrapper): string[] {
        return wrapper.findAll('.tiptap span[data-size]').map(span => `${span.text()}:${(span.element as HTMLElement).style.fontSize}`)
    }

    it('shows stored words in their size', async () => {
        const {wrapper} = await opened('Ein <span data-size="24">großes</span> Wort.')

        expect(shownSizes(wrapper)).toEqual(['großes:24px'])
    })

    it('shows a size set on selected words', async () => {
        const {wrapper, editor, stored} = await opened('Hallo Welt')
        await select(editor, {from: 7, to: 11})
        await pickSize(wrapper, '24')

        expect(shownSizes(wrapper)).toEqual(['Welt:24px'])
        expect(stored()).toBe('Hallo <span data-size="24">Welt</span>')
    })

    it('resizes the sized words the cursor stands in', async () => {
        const {wrapper, editor, stored} = await opened('Ein <span data-size="22">größeres</span> Wort.')
        await select(editor, 8)
        await pickSize(wrapper, '30')

        expect(shownSizes(wrapper)).toEqual(['größeres:30px'])
        expect(stored()).toBe('Ein <span data-size="30">größeres</span> Wort.')
        expect(editor.state.selection.from).toBe(8)
    })

    it('sizes the word the cursor stands in', async () => {
        const {wrapper, editor, stored} = await opened('Hallo Welt')
        await select(editor, 9)
        await pickSize(wrapper, '20')

        expect(shownSizes(wrapper)).toEqual(['Welt:20px'])
        expect(stored()).toBe('Hallo <span data-size="20">Welt</span>')
    })

    it('gives the sized words at the cursor their normal size back', async () => {
        const {wrapper, editor, stored} = await opened('Ein <span data-size="22">größeres</span> Wort.')
        await select(editor, 8)
        expect(wrapper.find('[data-testid="editor-size"]').text()).toBe('22 px')
        await wrapper.find('[data-testid="editor-size"]').trigger('click')
        await new DOMWrapper(document.querySelector<HTMLElement>('[data-testid="editor-size-reset"]')!).trigger('click')
        await settled()

        expect(shownSizes(wrapper)).toEqual([])
        expect(stored()).toBe('Ein größeres Wort.')
    })
})

/** Escape typed in the text is left to the page, so a dialog around the editor closes on it. */
describe('MarkdownEditor escape', () => {
    it('leaves escape unhandled', async () => {
        const wrapper = await mountSuspended(MarkdownEditor, {attachTo: document.body, props: {modelValue: 'Text'}})
        const text = wrapper.find('.tiptap').element
        const escape = new KeyboardEvent('keydown', {key: 'Escape', keyCode: 27, bubbles: true, cancelable: true})

        text.dispatchEvent(escape)

        expect(escape.defaultPrevented).toBe(false)
        wrapper.unmount()
    })
})
