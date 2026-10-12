/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import IconButton from './IconButton.vue'

/** An icon that opens a page is a link, so the page also opens in a new tab; one that acts is a button. */
describe('IconButton', () => {
    it('is a link to the page it opens', () => {
        const wrapper = mount(IconButton, {props: {icon: ['fas', 'pen'], label: 'Bearbeiten', to: '/station/members/edit/5'}})

        expect(wrapper.find('a').attributes('href')).toBe('/station/members/edit/5')
        expect(wrapper.find('a').attributes('aria-label')).toBe('Bearbeiten')
        expect(wrapper.find('button').exists()).toBe(false)
    })

    it('is a button without a page, and while disabled', () => {
        expect(mount(IconButton, {props: {icon: ['fas', 'pen'], label: 'Bearbeiten'}}).find('button').exists()).toBe(true)
        const disabled = mount(IconButton, {props: {icon: ['fas', 'pen'], label: 'Bearbeiten', to: '/x', disabled: true}})
        expect(disabled.find('a').exists()).toBe(false)
        expect(disabled.find('button').attributes('disabled')).toBeDefined()
    })
})
