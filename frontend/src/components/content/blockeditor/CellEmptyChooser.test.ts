/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {defineComponent, h} from 'vue'
import CellEmptyChooser from './CellEmptyChooser.vue'
import de from '@/i18n/de-DE'
import {CellContentType} from '@/api/generated/schema'
import {provideBlockEditorOptions} from '@/composables/useBlockEditorOptions'

/**
 * The chooser of an empty block offers what the surrounding editor allows: everything on a page, text
 * and pictures in a letter. It reads the page clipboard, which is request state, so it runs in the
 * Nuxt environment.
 */
describe('CellEmptyChooser', () => {
    const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de, en: {}}})

    function offered(allowedKinds?: CellContentType[]): string {
        const Host = defineComponent({
            setup() {
                if (allowedKinds) provideBlockEditorOptions({allowedKinds})
                return () => h(CellEmptyChooser)
            },
        })
        return mount(Host, {global: {plugins: [i18n]}}).text()
    }

    it('offers every kind of block where the editor names none', () => {
        const text = offered()

        expect(text).toContain(de.stationPages.editor.chooseVideo)
        expect(text).toContain(de.stationPages.editor.chooseMap)
    })

    it('offers only the kinds the editor allows', () => {
        const text = offered([CellContentType.MARKDOWN, CellContentType.IMAGE])

        expect(text).toContain(de.stationPages.editor.chooseMarkdown)
        expect(text).toContain(de.stationPages.editor.chooseImage)
        expect(text).not.toContain(de.stationPages.editor.chooseVideo)
        expect(text).not.toContain(de.stationPages.editor.catLayout)
    })
})
