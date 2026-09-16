/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import CoverablePicture from './CoverablePicture.vue'

/**
 * Dragging a cover over a picture.
 *
 * <p>The one thing that has to be right is the arithmetic: a cover is held in the picture's own
 * pixels and dragged on a screen showing it at some other width. Held in screen pixels it would move
 * as the window is resized and cover the wrong thing in the file that is finally sent.
 */
describe('CoverablePicture', () => {
    function picture(width = 1200, height = 800): HTMLCanvasElement {
        return {width, height, toDataURL: () => 'data:image/webp;base64,AA'} as unknown as HTMLCanvasElement
    }

    /** The picture is drawn at half its own width here, so a drag of 50 means 100. */
    function shownAtHalfWidth(view: ReturnType<typeof mount>) {
        const image = view.find('img').element as HTMLImageElement
        Object.defineProperty(image, 'clientWidth', {value: 600, configurable: true})
        image.getBoundingClientRect = () => ({
            left: 0, top: 0, width: 600, height: 400, right: 600, bottom: 400, x: 0, y: 0, toJSON: () => ({}),
        })
    }

    /** The cover that was added, which a test asserts against rather than the event plumbing. */
    function added(view: ReturnType<typeof mount>): unknown {
        const events = view.emitted('add')
        expect(events, 'a cover was added').toBeTruthy()
        return events?.[0]?.[0]
    }

    function drag(view: ReturnType<typeof mount>, from: [number, number], to: [number, number]) {
        view.find('[data-testid="coverable-picture"]').trigger('pointerdown', {clientX: from[0], clientY: from[1]})
        window.dispatchEvent(new window.PointerEvent('pointermove', {clientX: to[0], clientY: to[1]}))
        window.dispatchEvent(new window.PointerEvent('pointerup'))
    }

    it('reads a drag in the picture\'s pixels and not the screen\'s', async () => {
        const view = mount(CoverablePicture, {props: {picture: picture(), covers: []}})
        shownAtHalfWidth(view)

        drag(view, [10, 20], [60, 70])

        expect(added(view)).toMatchObject({x: 20, y: 40, width: 100, height: 100})
    })

    /** A drag the other way is the same rectangle, so a cover cannot have a negative width. */
    it('takes a drag upwards and leftwards as the same rectangle', async () => {
        const view = mount(CoverablePicture, {props: {picture: picture(), covers: []}})
        shownAtHalfWidth(view)

        drag(view, [60, 70], [10, 20])

        expect(added(view)).toMatchObject({x: 20, y: 40, width: 100, height: 100})
    })

    it('offers each cover for taking off again', async () => {
        const covers = [{x: 0, y: 0, width: 10, height: 10}, {x: 20, y: 20, width: 10, height: 10}]
        const view = mount(CoverablePicture, {props: {picture: picture(), covers}})

        const drawn = view.findAll('[data-testid="picture-cover"]')
        expect(drawn).toHaveLength(2)
        await drawn[1]!.trigger('click')

        expect(view.emitted('remove')?.[0]?.[0]).toBe(1)
    })

    /** Listeners go on the window, so an unmount while dragging must take them off again. */
    it('stops listening when it goes away', () => {
        const remove = vi.spyOn(window, 'removeEventListener')
        const view = mount(CoverablePicture, {props: {picture: picture(), covers: []}})
        shownAtHalfWidth(view)
        view.find('[data-testid="coverable-picture"]').trigger('pointerdown', {clientX: 1, clientY: 1})

        view.unmount()

        expect(remove).toHaveBeenCalledWith('pointermove', expect.any(Function))
        remove.mockRestore()
    })
})
