/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {zoomStep} from './pdfZoom'

/** Zooming the page of a PDF template in steps, from the whole page to four times its size. */
describe('zoomStep', () => {
    it('moves one step in and out', () => {
        expect(zoomStep(1, 1)).toBe(1.25)
        expect(zoomStep(2, 1)).toBe(3)
        expect(zoomStep(2, -1)).toBe(1.5)
    })

    it('stays at the whole page and at the largest step', () => {
        expect(zoomStep(1, -1)).toBe(1)
        expect(zoomStep(4, 1)).toBe(4)
    })
})
