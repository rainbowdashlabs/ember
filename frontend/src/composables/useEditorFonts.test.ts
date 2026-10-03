/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h, ref, type Ref} from 'vue'
import {FontOrigin, FontStyle, type DocumentFontsResponse, type FontFamilyOption} from '@/api/generated/schema'
import type {FontFileLoader} from '@/api/documentFonts'
import {useEditorFontArea, useEditorFonts, type EditorFonts} from './useEditorFonts'

/** A face as the browser's `FontFace` takes it, kept to look at. */
class FakeFontFace {
    constructor(readonly family: string, readonly source: ArrayBuffer, readonly descriptors: FontFaceDescriptors) {}

    load() {
        return Promise.resolve(this)
    }
}

function option(family: string, styles: FontStyle[], editorVersion: string | null): FontFamilyOption {
    return {family, origin: FontOrigin.STATION, styles, printsOnPdf: true, sample: 's', editorVersion}
}

const LIST: DocumentFontsResponse = {
    own: [],
    reachable: [
        option('Hausschrift', [FontStyle.REGULAR, FontStyle.BOLD], 'h1'),
        option('Zierschrift', [FontStyle.REGULAR], 'z1'),
        option('Libertinus Serif', [FontStyle.REGULAR], null),
    ],
    defaultFamily: 'Berlin Type Office',
    defaultStyles: [FontStyle.REGULAR],
}

/**
 * The template editor's fonts: only the families a template uses are loaded, each registered under a
 * name of the editor's own and set by a stylesheet scoped to the editor, a family without files keeps
 * the editor's look, and everything leaves the page with the editor.
 */
describe('useEditorFonts', () => {
    const fontSet = new Set<FakeFontFace>()
    let loaded: string[]
    let load: FontFileLoader

    beforeEach(() => {
        fontSet.clear()
        loaded = []
        load = vi.fn(async (family: string | null, style: FontStyle) => {
            loaded.push(`${family ?? 'default'} ${style}`)
            return new TextEncoder().encode(`${family} ${style}`).buffer as ArrayBuffer
        })
        vi.stubGlobal('FontFace', FakeFontFace)
        Object.defineProperty(document, 'fonts', {
            configurable: true,
            value: {add: (face: FakeFontFace) => fontSet.add(face), delete: (face: FakeFontFace) => fontSet.delete(face)},
        })
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        document.head.querySelectorAll('style[data-editor-fonts]').forEach(sheet => sheet.remove())
    })

    function open(used: Ref<(string | null)[]>, area: string | null = 'Hausschrift') {
        let fonts: EditorFonts | null = null
        const Area = defineComponent({
            setup() {
                const attrs = useEditorFontArea(() => area)
                return () => h('div', {...attrs.value, 'data-testid': 'area'}, [h('span', {'data-font': 'Hausschrift'}, 'Wort')])
            },
        })
        const Host = defineComponent({
            setup() {
                fonts = useEditorFonts(load, () => LIST, () => used.value)
                return () => h(Area)
            },
        })
        const wrapper = mount(Host, {attachTo: document.body})
        return {wrapper, fonts: () => fonts as unknown as EditorFonts}
    }

    function sheet(): HTMLStyleElement | null {
        return document.head.querySelector('style[data-editor-fonts]')
    }

    it('loads the files of the families the template uses, and no others', async () => {
        const {wrapper} = open(ref(['Hausschrift']))
        await flushPromises()

        expect(loaded).toEqual(['Hausschrift REGULAR', 'Hausschrift BOLD'])
        expect(load).toHaveBeenCalledWith('Hausschrift', FontStyle.REGULAR, 'h1')
        wrapper.unmount()
    })

    it('registers every style under a name of its own, a missing one from the regular file', async () => {
        const {wrapper, fonts} = open(ref(['hausschrift']))
        await flushPromises()

        const faces = [...fontSet]
        expect(faces).toHaveLength(4)
        const name = faces[0]?.family ?? ''
        expect(name).toMatch(new RegExp(`^ember-editor-${fonts().scope}-`))
        expect(faces.every(face => face.family === name)).toBe(true)
        const [regular, bold, italic, boldItalic] = faces
        expect(bold?.descriptors).toEqual({weight: '700', style: 'normal'})
        expect(italic?.descriptors).toEqual({weight: '400', style: 'italic'})
        expect(italic?.source).toBe(regular?.source)
        expect(boldItalic?.source).toBe(regular?.source)
        expect(bold?.source).not.toBe(regular?.source)
        expect(fonts().shown('Hausschrift')).toBe(true)
        expect(fonts().fontFamily('HAUSSCHRIFT')).toBe(`"${name}", sans-serif`)
        wrapper.unmount()
    })

    it('scopes the stylesheet and the page font to the editor', async () => {
        const {wrapper, fonts} = open(ref(['Hausschrift']))
        await flushPromises()

        const scope = fonts().scope
        const area = wrapper.get('[data-testid="area"]')
        expect(area.attributes('data-editor-fonts')).toBe(scope)
        expect(area.attributes('style')).toContain('--editor-page-font')
        const rules = sheet()?.textContent ?? ''
        expect(sheet()?.dataset.editorFonts).toBe(scope)
        expect(rules.split('\n').every(rule => rule.startsWith(`[data-editor-fonts="${scope}"]`))).toBe(true)
        expect(rules).toContain('[data-font="Hausschrift" i]')
        wrapper.unmount()
    })

    it('leaves a family without files and its words in the editor look', async () => {
        const {wrapper, fonts} = open(ref(['Libertinus Serif']), 'Libertinus Serif')
        await flushPromises()

        expect(loaded).toEqual([])
        expect(fonts().shown('Libertinus Serif')).toBe(false)
        expect(fonts().fontFamily('Libertinus Serif')).toBeUndefined()
        expect(wrapper.get('[data-testid="area"]').attributes('style')).toBeUndefined()
        wrapper.unmount()
    })

    it('shows a family out of reach in the default font it prints in', async () => {
        const {wrapper, fonts} = open(ref(['Gelöscht']), 'Gelöscht')
        await flushPromises()

        expect(loaded).toEqual(['default REGULAR'])
        expect(fonts().fontFamily('Gelöscht')).toBe(fonts().fontFamily(null))
        expect(fonts().shown('Gelöscht')).toBe(false)
        wrapper.unmount()
    })

    it('loads a family when the template starts to use it, once', async () => {
        const used = ref<(string | null)[]>([null])
        const {wrapper} = open(used)
        await flushPromises()
        expect(loaded).toEqual(['default REGULAR'])

        used.value = [null, 'Zierschrift', 'zierschrift']
        await flushPromises()
        used.value = [null, 'Zierschrift']
        await flushPromises()
        expect(loaded).toEqual(['default REGULAR', 'Zierschrift REGULAR'])
        wrapper.unmount()
    })

    it('takes the faces and the stylesheet away when the editor closes', async () => {
        const {wrapper} = open(ref(['Hausschrift', null]))
        await flushPromises()
        expect(fontSet.size).toBe(8)
        expect(sheet()).not.toBeNull()

        wrapper.unmount()
        expect(fontSet.size).toBe(0)
        expect(sheet()).toBeNull()
    })

    it('keeps a family whose file cannot be loaded in the editor look', async () => {
        load = vi.fn(async () => {
            throw new Error('gone')
        })
        const {wrapper, fonts} = open(ref(['Hausschrift']))
        await flushPromises()

        expect(fonts().shown('Hausschrift')).toBe(false)
        expect(fontSet.size).toBe(0)
        wrapper.unmount()
    })
})
