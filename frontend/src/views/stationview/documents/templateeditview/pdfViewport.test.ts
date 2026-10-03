/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {fieldRectOf, keptOnCanvas, scaleOf, screenBoxOf, toPdf, toScreen, type PageGeometry} from './pdfViewport'

/**
 * An A4 page as pdf.js draws it, cropped where a crop box is given, turned and scaled.
 *
 * <p>pdf.js does not hand its `PageViewport` out at run time, so the matrix is built here the way its
 * constructor builds it. The first tests pin it to what a reader sees, corner by corner.
 */
function viewport(rotation: number, scale = 1, viewBox = [0, 0, 595, 842]): PageGeometry {
    const [x1 = 0, y1 = 0, x2 = 0, y2 = 0] = viewBox
    const centerX = (x1 + x2) / 2
    const centerY = (y1 + y2) / 2
    const turns: Readonly<Record<number, readonly [number, number, number, number]>> = {
        0: [1, 0, 0, -1],
        90: [0, 1, 1, 0],
        180: [-1, 0, 0, 1],
        270: [0, -1, -1, 0],
    }
    const [a, b, c, d] = turns[rotation] ?? [1, 0, 0, -1]
    const sideways = a === 0
    const offsetX = (sideways ? Math.abs(centerY - y1) : Math.abs(centerX - x1)) * scale
    const offsetY = (sideways ? Math.abs(centerX - x1) : Math.abs(centerY - y1)) * scale
    return {
        transform: [
            a * scale, b * scale, c * scale, d * scale,
            offsetX - a * scale * centerX - c * scale * centerY,
            offsetY - b * scale * centerX - d * scale * centerY,
        ],
        width: (sideways ? y2 - y1 : x2 - x1) * scale,
        height: (sideways ? x2 - x1 : y2 - y1) * scale,
    }
}

/**
 * The editor draws boxes in CSS pixels over the canvas and stores them in PDF points of the unturned
 * page. The corners are checked against what a reader sees: a page turned a quarter clockwise shows
 * its bottom left corner at the top left.
 */
describe('the PDF viewport conversion', () => {
    it('puts the bottom left of an unturned page at the bottom left of the canvas', () => {
        const page = viewport(0)

        expect(toScreen(page, 0, 0)).toEqual([0, 842])
        expect(toScreen(page, 595, 842)).toEqual([595, 0])
    })

    it('puts the bottom left of a page turned a quarter clockwise at the top left', () => {
        const page = viewport(90)

        expect(page.width).toBe(842)
        expect(toScreen(page, 0, 0)).toEqual([0, 0])
        expect(toScreen(page, 0, 842)).toEqual([842, 0])
    })

    it('turns the other quarters the same way: upside down to the top right, a quarter back to the bottom right', () => {
        expect(toScreen(viewport(180), 0, 0)).toEqual([595, 0])
        expect(toScreen(viewport(270), 0, 0)).toEqual([842, 595])
    })

    it('measures a cropped page from its crop box', () => {
        const page = viewport(0, 1, [50, 80, 450, 680])

        expect(page.width).toBe(400)
        expect(toScreen(page, 50, 80)).toEqual([0, 600])
    })

    it.each([0, 90, 180, 270])('brings a box back to the same points after a turn of %i', rotation => {
        const page = viewport(rotation, 1.5, [20, 30, 575, 812])
        const rect = {page: 2, x: 100, y: 200, width: 150, height: 20}

        const box = screenBoxOf(page, rect)

        expect(fieldRectOf(page, 2, box)).toEqual(rect)
        expect(box.width).toBeCloseTo(rotation % 180 === 0 ? 225 : 30)
    })

    it('reads a point back onto the page', () => {
        const page = viewport(270, 2)

        const [x, y] = toPdf(page, ...toScreen(page, 123, 456))

        expect(x).toBeCloseTo(123)
        expect(y).toBeCloseTo(456)
        expect(scaleOf(page)).toBeCloseTo(2)
    })

    it('keeps a box dragged over the edge on the canvas', () => {
        const page = viewport(0)

        expect(keptOnCanvas(page, {left: -20, top: 830, width: 100, height: 30}))
            .toEqual({left: 0, top: 812, width: 100, height: 30})
        expect(keptOnCanvas(page, {left: 10, top: 10, width: 900, height: 0}))
            .toEqual({left: 0, top: 10, width: 595, height: 1})
    })
})
