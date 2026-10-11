/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {LARGEST_TEXT_SIZE, pointSize, pointsOfPixels, SMALLEST_TEXT_SIZE} from './textSize'

/** A size words can be set in is a whole number of points within bounds, typed or stored. */
describe('pointSize', () => {
    it.each([
        ['11', 11],
        [' 20 ', 20],
        [18, 18],
        [String(SMALLEST_TEXT_SIZE), SMALLEST_TEXT_SIZE],
        [String(LARGEST_TEXT_SIZE), LARGEST_TEXT_SIZE],
    ])('takes %j as %d points', (input, size) => {
        expect(pointSize(input)).toBe(size)
    })

    it.each([
        String(SMALLEST_TEXT_SIZE - 1),
        String(LARGEST_TEXT_SIZE + 1),
        '0',
        '-12',
        '12.5',
        '12pt',
        '1e2',
        '',
        '   ',
        null,
        undefined,
    ])('refuses %j', input => {
        expect(pointSize(input)).toBeNull()
    })
})

/** A size in pixels, stored before sizes were in points or measured on screen, comes to the nearest point. */
describe('pointsOfPixels', () => {
    it.each([
        [24, 18],
        [14, 11],
        [15, 11],
        [16, 12],
    ])('reads %d pixels as %d points', (pixels, points) => {
        expect(pointsOfPixels(pixels)).toBe(points)
    })

    it.each([0, 500, Number.NaN])('refuses %d pixels', pixels => {
        expect(pointsOfPixels(pixels)).toBeNull()
    })
})
