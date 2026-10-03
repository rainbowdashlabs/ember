/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, mount} from '@vue/test-utils'
import {FontOrigin, FontStyle, type FontFamilyOption} from '@/api/generated/schema'
import EditorToolbar from './EditorToolbar.vue'

enableAutoUnmount(afterEach)

const FONTS: FontFamilyOption[] = [
    {family: 'Hausschrift', origin: FontOrigin.ASSOCIATION, styles: [FontStyle.REGULAR], printsOnPdf: true},
]

/**
 * The text editor's menu offers a font only where it is handed families, which is a letter's text block;
 * a page, a news entry or a wiki article hands none.
 */
describe('EditorToolbar', () => {
    it('offers no font where no families are given', () => {
        expect(mount(EditorToolbar, {props: {editor: undefined}}).find('[data-testid="editor-font"]').exists())
            .toBe(false)
    })

    it('offers the template font and every family given', async () => {
        const wrapper = mount(EditorToolbar, {props: {editor: undefined, fonts: FONTS}, attachTo: document.body})
        await wrapper.find('[data-testid="editor-font"]').trigger('click')

        const entries = [...document.querySelectorAll('[role="menuitemradio"]')].map(entry => entry.textContent?.trim())
        expect(entries).toEqual(['Schrift der Vorlage', 'Hausschrift (Verband)'])
    })
})
