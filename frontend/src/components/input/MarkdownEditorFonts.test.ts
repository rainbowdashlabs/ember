/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h, ref} from 'vue'
import {FontOrigin, FontStyle, type DocumentFontsResponse} from '@/api/generated/schema'
import {useEditorFontArea, useEditorFonts} from '@/composables/useEditorFonts'
import MarkdownEditor from './MarkdownEditor.vue'

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({sessionInfo: ref(null)}),
}))

enableAutoUnmount(afterEach)

const LIST: DocumentFontsResponse = {
    own: [],
    reachable: [
        {family: 'Hausschrift', origin: FontOrigin.STATION, styles: [FontStyle.REGULAR], printsOnPdf: true, sample: 's', editorVersion: 'h1'},
        {family: 'Libertinus Serif', origin: FontOrigin.BUILT_IN, styles: [FontStyle.REGULAR], printsOnPdf: false, sample: 't', editorVersion: null},
    ],
    defaultFamily: 'Liberation Sans',
    defaultStyles: [],
}

const TEXT = 'Ein <span data-font="Hausschrift">eigenes</span> und ein <span data-font="Libertinus Serif">fremdes</span> Wort.'

/** A face as the browser's `FontFace` takes it. */
class FakeFontFace {
    constructor(readonly family: string) {}

    load() {
        return Promise.resolve(this)
    }
}

/**
 * The text editor inside the template editor: words set in a family whose files were loaded show in it,
 * and words in a family without files keep the dotted marker and the name on hover.
 */
describe('MarkdownEditor in the template editor', () => {
    beforeEach(() => {
        vi.stubGlobal('FontFace', FakeFontFace)
        Object.defineProperty(document, 'fonts', {configurable: true, value: {add: vi.fn(), delete: vi.fn()}})
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    async function open() {
        const Area = defineComponent({
            setup() {
                useEditorFontArea(() => null)
                return () => h(MarkdownEditor, {modelValue: TEXT, fonts: LIST.reachable})
            },
        })
        const Host = defineComponent({
            setup() {
                useEditorFonts(async () => new ArrayBuffer(4), () => LIST, () => ['Hausschrift', 'Libertinus Serif'])
                return () => h(Area)
            },
        })
        const wrapper = mount(Host, {attachTo: document.body, global: {stubs: {MediaBrowseModal: true}}})
        await flushPromises()
        await vi.waitFor(() => expect(wrapper.find('.tiptap [data-font="Hausschrift"]').exists()).toBe(true))
        return wrapper
    }

    it('sets a marked word in the family the editor loaded', async () => {
        const wrapper = await open()
        const word = wrapper.get('.tiptap [data-font="Hausschrift"]').element

        expect(word.closest('[data-editor-fonts]')).not.toBeNull()
        expect(getComputedStyle(word).fontFamily).toMatch(/^"?ember-editor-.+"?, sans-serif$/)
        expect(getComputedStyle(word).textDecorationLine).not.toBe('underline')
    })

    it('keeps the marker on a word in a family without files', async () => {
        const wrapper = await open()
        const word = wrapper.get('.tiptap [data-font="Libertinus Serif"]')

        expect(word.attributes('title')).toBe('Libertinus Serif')
        expect(word.classes()).toContain('text-font')
        expect(getComputedStyle(word.element).fontFamily).not.toMatch(/ember-editor/)
    })
})
