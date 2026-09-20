/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {PhFireTruck} from '@phosphor-icons/vue'
import AppIcon from './AppIcon.vue'

/**
 * The two sets behind one prop.
 *
 * <p>The Phosphor case is the one worth a test: the icon is named as a string and looked up among
 * the components the plugin registered, so a rename on either side fails as an element nobody
 * recognises rather than as an error. Mounting it with the same registration the plugin makes is
 * what catches that.
 */
describe('AppIcon', () => {
    it('draws a FontAwesome icon through the global component', () => {
        const icon = mount(AppIcon, {props: {icon: ['fas', 'user']}})

        expect(icon.find('[data-testid="icon"]').exists()).toBe(true)
    })

    it('draws a Phosphor icon by resolving the name the plugin registered', () => {
        const icon = mount(AppIcon, {
            props: {icon: ['ph', 'fire-truck']},
            global: {components: {PhFireTruck}},
        })

        expect(icon.find('svg').exists()).toBe(true)
        expect(icon.html()).not.toContain('phfiretruck')
    })

    it('asks Phosphor for the weight that matches FontAwesome', () => {
        const icon = mount(AppIcon, {
            props: {icon: ['ph', 'fire-truck']},
            global: {components: {PhFireTruck}},
        })

        expect(icon.findComponent(PhFireTruck).props('weight')).toBe('fill')
    })

    it('passes a height and width on to the icon it draws', () => {
        const icon = mount(AppIcon, {
            props: {icon: ['ph', 'fire-truck']},
            attrs: {class: 'h-4 w-4'},
            global: {components: {PhFireTruck}},
        })

        expect(icon.find('svg').classes()).toContain('h-4')
    })
})
