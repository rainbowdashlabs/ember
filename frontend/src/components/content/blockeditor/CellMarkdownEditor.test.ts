/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {defineComponent, h} from 'vue'
import {FontOrigin, FontStyle, type FontFamilyOption} from '@/api/generated/schema'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import {provideBlockEditorOptions} from '@/composables/useBlockEditorOptions'
import CellMarkdownEditor from './CellMarkdownEditor.vue'

const FONTS: FontFamilyOption[] = [
    {family: 'Hausschrift', origin: FontOrigin.STATION, styles: [FontStyle.REGULAR], printsOnPdf: true, sample: 'v1'},
]

/**
 * A text block hands its editor the font families only where the surrounding editor names them, which
 * a letter does; pages, news entries and wiki articles provide none, so their menu has no font.
 */
describe('CellMarkdownEditor', () => {
    function fontsHanded(textFonts?: readonly FontFamilyOption[]) {
        const Host = defineComponent({
            setup() {
                if (textFonts) provideBlockEditorOptions({textFonts})
                return () => h(CellMarkdownEditor, {content: ''})
            },
        })
        return mount(Host, {global: {stubs: {MarkdownEditor: true}}}).findComponent(MarkdownEditor).props('fonts')
    }

    it('hands no fonts where the editor names none', () => {
        expect(fontsHanded()).toBeUndefined()
    })

    it('hands the families the editor names', () => {
        expect(fontsHanded(FONTS)).toEqual(FONTS)
    })
})
