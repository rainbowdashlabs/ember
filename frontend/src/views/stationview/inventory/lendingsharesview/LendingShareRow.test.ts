/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import type {ShareDetail} from '@/api/generated/schema'
import LendingShareRow from './LendingShareRow.vue'

function detail(over: Partial<ShareDetail>): ShareDetail {
    return {
        share: {id: 1, inventoryId: 2, artId: null, itemId: null, shareScope: 'ALL_PARTNERS'},
        inventoryName: 'Zelte',
        partners: [],
        pictureKey: null,
        icon: null,
        color: null,
        homogeneous: true,
        ...over,
    } as unknown as ShareDetail
}

/** A row of the shares overview is shown with the photo of what it offers, or its icon. */
describe('LendingShareRow', () => {
    it('shows the photo of the offered thing by its key', () => {
        const wrapper = mount(LendingShareRow, {
            props: {detail: detail({pictureKey: 'abc'}), label: 'Zelte'},
            global: {stubs: {AuthImage: {props: ['src'], template: '<img :src="src">'}, EditButton: true}},
        })

        expect(wrapper.find('img').attributes('src')).toContain('/inventory-pictures/abc')
    })

    it('draws the icon where there is no photo', () => {
        const wrapper = mount(LendingShareRow, {
            props: {detail: detail({icon: 'tent'}), label: 'Zelte'},
            global: {stubs: {GearGlyph: true, EditButton: true}},
        })

        expect(wrapper.findComponent({name: 'GearGlyph'}).props('glyph').icon).toBe('tent')
    })

    it('draws a drawer of different things without an icon as a box', () => {
        const wrapper = mount(LendingShareRow, {
            props: {detail: detail({homogeneous: false}), label: 'Zelte'},
            global: {stubs: {GearGlyph: true, EditButton: true}},
        })

        expect(wrapper.findComponent({name: 'GearGlyph'}).props('glyph').icon).toBe('box')
    })
})
