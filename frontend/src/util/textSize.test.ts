/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {LARGEST_TEXT_SIZE, pixelSize, SMALLEST_TEXT_SIZE} from './textSize'

/** A size words can be set in is a whole number of pixels within bounds, typed or stored. */
describe('pixelSize', () => {
    it.each([
        ['14', 14],
        [' 20 ', 20],
        [18, 18],
        [String(SMALLEST_TEXT_SIZE), SMALLEST_TEXT_SIZE],
        [String(LARGEST_TEXT_SIZE), LARGEST_TEXT_SIZE],
    ])('takes %j as %d pixels', (input, size) => {
        expect(pixelSize(input)).toBe(size)
    })

    it.each([
        String(SMALLEST_TEXT_SIZE - 1),
        String(LARGEST_TEXT_SIZE + 1),
        '0',
        '-12',
        '12.5',
        '12px',
        '1e2',
        '',
        '   ',
        null,
        undefined,
    ])('refuses %j', input => {
        expect(pixelSize(input)).toBeNull()
    })
})
