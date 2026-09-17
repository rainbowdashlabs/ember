/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it, vi} from 'vitest'
import {DEFAULT_IMAGE_BUDGET, prepareImageUpload, scaledSize} from './imageUpload'

/**
 * A phone camera writes far more picture than any screen shows and far more bytes than the
 * endpoints take. What has to hold is that shrinking it keeps its proportions, because a photo
 * squeezed into a square is a photo nobody recognises the lost glove in.
 */
describe('fitting a picked picture into what may be sent', () => {
    it('leaves a picture that already fits at the size it has', () => {
        expect(scaledSize(800, 600, 2048)).toEqual({width: 800, height: 600})
        expect(scaledSize(2048, 100, 2048)).toEqual({width: 2048, height: 100})
    })

    it('shrinks the longest edge to the budget and keeps the proportions', () => {
        expect(scaledSize(4000, 3000, 2000)).toEqual({width: 2000, height: 1500})
        expect(scaledSize(3000, 4000, 2000)).toEqual({width: 1500, height: 2000})
    })

    it('never shrinks an edge away entirely', () => {
        expect(scaledSize(10000, 3, 100)).toEqual({width: 100, height: 1})
    })

    it('has nothing to do with a picture of no size', () => {
        expect(scaledSize(0, 0, 2048)).toEqual({width: 0, height: 0})
    })

    it('stays under what the endpoints accept', () => {
        expect(DEFAULT_IMAGE_BUDGET.maxBytes).toBeLessThan(5 * 1024 * 1024)
    })
})

/**
 * Where the work happens, which decides whether the page answers while it does.
 *
 * <p>Encoding a photograph is several hundred milliseconds of arithmetic. On the screen's own thread
 * that is a page that does not respond, and what people did was press the button again and ask
 * whether it had worked. The surface is the whole of the fix: the offscreen one takes the encoding
 * somewhere else, and the frame waited for before any of it starts is what lets the spinner appear.
 */
describe('where a picture is redrawn', () => {

    it('asks for an offscreen surface where the browser has one', async () => {
        const made: number[][] = []
        class FakeOffscreen {
            constructor(public width: number, public height: number) {
                made.push([width, height])
            }

            getContext() {
                return {drawImage: () => {}}
            }

            convertToBlob() {
                return Promise.resolve(new Blob(['x'], {type: 'image/jpeg'}))
            }
        }
        vi.stubGlobal('OffscreenCanvas', FakeOffscreen)
        vi.stubGlobal('createImageBitmap', () =>
            Promise.resolve({width: 4000, height: 3000, close: () => {}}))

        const prepared = await prepareImageUpload(new File(['x'], 'photo.heic', {type: 'image/heic'}))

        expect(made, 'the surface was the offscreen one').toEqual([[2048, 1536]])
        expect(prepared.type).toBe('image/jpeg')
        expect(prepared.name).toBe('photo.jpg')
        vi.unstubAllGlobals()
    })

    it('still works where the browser has no offscreen canvas', async () => {
        vi.stubGlobal('OffscreenCanvas', undefined)
        vi.stubGlobal('createImageBitmap', () =>
            Promise.resolve({width: 100, height: 100, close: () => {}}))
        const canvas = {
            width: 0,
            height: 0,
            getContext: () => ({drawImage: () => {}}),
            toBlob: (back: (b: Blob) => void) => back(new Blob(['x'], {type: 'image/jpeg'})),
        }
        vi.stubGlobal('document', {createElement: () => canvas})

        const prepared = await prepareImageUpload(new File(['x'], 'photo.png', {type: 'image/png'}))

        expect(prepared.type).toBe('image/jpeg')
        vi.unstubAllGlobals()
    })
})
