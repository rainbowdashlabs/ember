/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import SignatureTyped from './SignatureTyped.vue'

enableAutoUnmount(afterEach)

/** The texts drawn onto every sheet, in order. */
let drawn: string[] = []

beforeEach(() => {
    drawn = []
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(() => ({
        font: '',
        fillStyle: '',
        textBaseline: '',
        measureText: (text: string) => ({width: text.length * 10}),
        fillText: (text: string) => drawn.push(text),
    }) as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue('data:image/png;base64,AAAA')
})

afterEach(() => {
    vi.restoreAllMocks()
})

describe('SignatureTyped', () => {
    it('takes a name of at most 80 characters', () => {
        const wrapper = mount(SignatureTyped)

        expect(wrapper.get('input').attributes('maxlength')).toBe('80')
    })

    it('draws no more than 80 characters of a longer name pasted past the bound', async () => {
        const wrapper = mount(SignatureTyped)
        const long = 'Maximiliane '.repeat(10)

        await wrapper.get('input').setValue(long)
        await flushPromises()

        const shown = long.slice(0, 80).trim()
        expect(drawn.at(-1)).toBe(shown)
        expect(wrapper.get('img').attributes('alt')).toContain(shown)
        expect(wrapper.emitted('change')?.at(-1)).toEqual(['data:image/png;base64,AAAA'])
    })
})
