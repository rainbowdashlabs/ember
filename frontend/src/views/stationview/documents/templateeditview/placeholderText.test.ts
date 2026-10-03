/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {withPlaceholder} from './placeholderText'

describe('a placeholder put at the end of a text', () => {
    it('stands alone in an empty text', () => {
        expect(withPlaceholder('', 'today')).toBe('{{today}}')
    })

    it('gets a space after a word, and none after a space or a line break', () => {
        expect(withPlaceholder('Berlin,', 'today')).toBe('Berlin, {{today}}')
        expect(withPlaceholder('Berlin, ', 'today')).toBe('Berlin, {{today}}')
        expect(withPlaceholder('Wache\n', 'station.name')).toBe('Wache\n{{station.name}}')
    })
})
