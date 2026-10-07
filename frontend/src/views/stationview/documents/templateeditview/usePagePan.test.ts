/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {mount, type VueWrapper} from '@vue/test-utils'
import {defineComponent, h, nextTick, ref} from 'vue'
import {usePagePan} from './usePagePan'

let wrapper: VueWrapper | null = null

afterEach(() => {
    wrapper?.unmount()
    wrapper = null
})

/** A box that scrolls, with the pan on it, as the PDF editor's page area has. */
async function scroller() {
    const Box = defineComponent(() => {
        const box = ref<HTMLElement | null>(null)
        const panning = usePagePan(box)
        return () => h('div', {ref: box, 'data-panning': String(panning.value)})
    })
    wrapper = mount(Box, {attachTo: document.body})
    await nextTick()
    const element = wrapper.element as HTMLElement
    element.setPointerCapture = () => undefined
    return element
}

function press(element: HTMLElement, type: string, x: number, y: number, pointerType = 'mouse') {
    element.dispatchEvent(new PointerEvent(type, {clientX: x, clientY: y, pointerType, button: 0, pointerId: 1}))
}

/** Dragging the page with a mouse, for a mouse without a wheel that scrolls both ways. */
describe('usePagePan', () => {
    it('scrolls the box against the drag and stops when let go', async () => {
        const element = await scroller()
        element.scrollLeft = 100
        element.scrollTop = 100

        press(element, 'pointerdown', 50, 50)
        await nextTick()
        expect(element.dataset.panning).toBe('true')
        press(element, 'pointermove', 30, 20)
        press(element, 'pointerup', 30, 20)
        press(element, 'pointermove', 0, 0)

        expect(element.scrollLeft).toBe(120)
        expect(element.scrollTop).toBe(130)
    })

    it('leaves a finger to the browser', async () => {
        const element = await scroller()
        element.scrollLeft = 100

        press(element, 'pointerdown', 50, 50, 'touch')
        press(element, 'pointermove', 30, 50, 'touch')

        expect(element.scrollLeft).toBe(100)
    })
})
