/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FieldRect} from '@/api/generated/schema'

/**
 * What the field editor needs of a pdf.js viewport: the matrix that maps a point of the page, in PDF
 * points of the page's own unturned user space, onto the canvas in CSS pixels, and the size of the
 * canvas. Rotation, scale and the offset of a crop box that does not start at zero are all in the
 * matrix, so nothing else has to be asked of the page.
 */
export interface PageGeometry {
    transform: readonly number[]
    width: number
    height: number
}

/** A box on the canvas in CSS pixels, measured from the canvas's top left. */
export interface ScreenBox {
    left: number
    top: number
    width: number
    height: number
}

/** A point of the page mapped onto the canvas. */
export function toScreen(geometry: PageGeometry, x: number, y: number): [number, number] {
    const [a = 1, b = 0, c = 0, d = 1, e = 0, f = 0] = geometry.transform
    return [a * x + c * y + e, b * x + d * y + f]
}

/** A point of the canvas mapped back onto the page. */
export function toPdf(geometry: PageGeometry, left: number, top: number): [number, number] {
    const [a = 1, b = 0, c = 0, d = 1, e = 0, f = 0] = geometry.transform
    const determinant = a * d - b * c
    const dx = left - e
    const dy = top - f
    return [(d * dx - c * dy) / determinant, (a * dy - b * dx) / determinant]
}

/** How many CSS pixels a PDF point is drawn as. */
export function scaleOf(geometry: PageGeometry): number {
    const [a = 1, b = 0] = geometry.transform
    return Math.hypot(a, b)
}

/**
 * Where a stored box shows on the canvas. A box stays a box under quarter turns, so its two opposite
 * corners are enough.
 */
export function screenBoxOf(geometry: PageGeometry, rect: FieldRect): ScreenBox {
    const [x1, y1] = toScreen(geometry, rect.x, rect.y)
    const [x2, y2] = toScreen(geometry, rect.x + rect.width, rect.y + rect.height)
    return {
        left: Math.min(x1, x2),
        top: Math.min(y1, y2),
        width: Math.abs(x2 - x1),
        height: Math.abs(y2 - y1),
    }
}

/**
 * The box as it is stored, from where it shows on the canvas: PDF points of the unturned page, origin
 * at the bottom left, rounded to a hundredth of a point.
 */
export function fieldRectOf(geometry: PageGeometry, page: number, box: ScreenBox): FieldRect {
    const [x1, y1] = toPdf(geometry, box.left, box.top)
    const [x2, y2] = toPdf(geometry, box.left + box.width, box.top + box.height)
    return {
        page,
        x: round(Math.min(x1, x2)),
        y: round(Math.min(y1, y2)),
        width: round(Math.abs(x2 - x1)),
        height: round(Math.abs(y2 - y1)),
    }
}

/** A box kept on the canvas: no wider or taller than it, and moved back inside where it hangs over. */
export function keptOnCanvas(geometry: PageGeometry, box: ScreenBox): ScreenBox {
    const width = Math.min(Math.max(box.width, 1), geometry.width)
    const height = Math.min(Math.max(box.height, 1), geometry.height)
    return {
        left: Math.min(Math.max(box.left, 0), geometry.width - width),
        top: Math.min(Math.max(box.top, 0), geometry.height - height),
        width,
        height,
    }
}

/** A hundredth of a point is finer than any form needs; a negative zero becomes a plain one. */
function round(value: number): number {
    return Math.round(value * 100) / 100 || 0
}
