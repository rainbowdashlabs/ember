/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {presetOf} from './useSavedFilters'

describe('useSavedFilters', () => {
    it('brings back the empty filters a saved filter was stored with', () => {
        const stored = JSON.stringify({tab: 'MEMBER', multiFilters: {groups: ['Jugend']}, emptyFilters: ['12']})

        const preset = presetOf(4, 'Ohne Ausweis', stored)

        expect(preset.emptyFilters).toEqual(['12'])
        expect(preset.multiFilters).toEqual({groups: ['Jugend']})
        expect(preset.tab).toBe('MEMBER')
    })

    it('reads a filter saved before empty filters were kept as having none', () => {
        const preset = presetOf(5, 'Alt', JSON.stringify({tab: 'ALL', multiFilters: {}}))

        expect(preset.emptyFilters).toEqual([])
    })
})
