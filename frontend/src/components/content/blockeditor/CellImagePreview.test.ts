/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import CellImagePreview from './CellImagePreview.vue'
import {ImageAlign, type ImageConfig} from '@/api/generated/schema'

function marginOf(config: ImageConfig): string {
    const wrapper = mount(CellImagePreview, {props: {src: '/picture.png', config}})
    return (wrapper.element as HTMLElement).style.margin
}

/** A picture narrower than its column stands where its alignment says, in the middle where none is set. */
describe('CellImagePreview', () => {
    it.each([
        [ImageAlign.LEFT, '0px auto 0px 0px'],
        [ImageAlign.CENTER, '0px auto'],
        [ImageAlign.RIGHT, '0px 0px 0px auto'],
    ])('stands %s', (imageAlign, margin) => {
        expect(marginOf({imageAlign, maxHeight: 100})).toBe(margin)
    })

    it('stands in the middle without an alignment', () => {
        expect(marginOf({maxHeight: 100})).toBe('0px auto')
    })
})
