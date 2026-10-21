/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import ReachedFamilyList from './ReachedFamilyList.vue'
import {FontOrigin, FontStyle, type FontFamilyOption} from '@/api/generated/schema'

const BUILT_IN: FontFamilyOption = {
    family: 'Liberation Serif',
    origin: FontOrigin.BUILT_IN,
    styles: [FontStyle.REGULAR],
    sample: 's1',
    editorVersion: null,
    printsOnPdf: true,
    printsInLetters: true,
}

/** The fonts page lists the default font every text prints in, beside the families reached from further up. */
describe('ReachedFamilyList', () => {
    it('lists the default font first, marked as the default', () => {
        const wrapper = mount(ReachedFamilyList, {
            props: {families: [BUILT_IN], defaultFamily: 'Berlin Type', defaultStyles: [FontStyle.REGULAR, FontStyle.BOLD]},
        })

        const entries = wrapper.findAll('li')
        expect(entries).toHaveLength(2)
        expect(entries[0]!.attributes('data-testid')).toBe('font-reached-default')
        expect(entries[0]!.text()).toContain('Berlin Type')
        expect(entries[0]!.text()).toContain('Standard')
        expect(entries[0]!.text()).toContain('Normal, Fett')
        expect(entries[1]!.text()).toContain('Liberation Serif')
    })

    it('lists no default entry where the instance has no default font name', () => {
        const wrapper = mount(ReachedFamilyList, {props: {families: [BUILT_IN], defaultFamily: '', defaultStyles: []}})

        expect(wrapper.find('[data-testid="font-reached-default"]').exists()).toBe(false)
    })
})
