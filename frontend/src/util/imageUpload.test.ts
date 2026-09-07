/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {DEFAULT_IMAGE_BUDGET, scaledSize} from './imageUpload'

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
