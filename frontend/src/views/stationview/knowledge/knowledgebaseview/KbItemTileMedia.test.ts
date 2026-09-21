/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import KbItemTileMedia from './KbItemTileMedia.vue'
import type {KbItem} from './useKbItems'

/**
 * The top of a wiki tile, which shows what a file is where there is a picture of it and what kind
 * of file it is everywhere else.
 */

const AuthImage = {name: 'AuthImage', props: ['src', 'alt'], template: '<img :src="src" :alt="alt"/>'}
const FontAwesomeIcon = {name: 'FontAwesomeIcon', props: ['icon'], template: '<i data-testid="icon"/>'}

function tile(overrides: Partial<KbItem>) {
    const item: KbItem = {
        key: 'file-1',
        icon: ['fas', 'file-pdf'],
        iconClass: '',
        title: 'Einsatzplan',
        typeLabel: 'PDF',
        restricted: false,
        favourite: false,
        actions: [],
        ...overrides,
    }
    return mount(KbItemTileMedia, {
        props: {item},
        global: {stubs: {AuthImage, 'font-awesome-icon': FontAwesomeIcon}},
    })
}

describe('KbItemTileMedia', () => {
    it('draws the picture of a file that has one', () => {
        const wrapper = tile({picture: '/kb/files/1/picture?size=512'})

        expect(wrapper.find('img').attributes('src')).toBe('/kb/files/1/picture?size=512')
    })

    it('draws the icon of a file that has none', () => {
        const wrapper = tile({})

        expect(wrapper.find('img').exists()).toBe(false)
        expect(wrapper.find('[data-testid="icon"]').exists()).toBe(true)
    })

    /** A folder's own icon is not a picture of anything, and it is drawn small rather than as a cover. */
    it('draws a folder icon rather than a cover where there is no picture', () => {
        const wrapper = tile({imageUrl: '/kb/folders/3/icon'})

        expect(wrapper.find('img').attributes('src')).toBe('/kb/folders/3/icon')
        expect(wrapper.find('img').classes()).toContain('h-16')
    })
})
