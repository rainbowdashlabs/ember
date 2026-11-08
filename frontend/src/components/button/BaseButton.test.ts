/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import BaseButton from './BaseButton.vue'
import SecondaryButton from './SecondaryButton.vue'

/** A button that opens a page is a link, so the page also opens in a new tab; one that acts stays a button. */
describe('BaseButton', () => {
    it('is a link to the page it opens, also through a coloured variant', () => {
        const wrapper = mount(SecondaryButton, {props: {to: '/station/inventory/movements/7'}, slots: {default: 'Ablauf ansehen'}})

        expect(wrapper.find('a').attributes('href')).toBe('/station/inventory/movements/7')
        expect(wrapper.text()).toBe('Ablauf ansehen')
        expect(wrapper.find('button').exists()).toBe(false)
    })

    it('is a button without a page, and while disabled', () => {
        expect(mount(BaseButton, {slots: {default: 'Speichern'}}).find('button').exists()).toBe(true)
        const disabled = mount(BaseButton, {props: {to: '/x', disabled: true}, slots: {default: 'Öffnen'}})
        expect(disabled.find('a').exists()).toBe(false)
        expect(disabled.find('button').attributes('disabled')).toBeDefined()
    })
})
