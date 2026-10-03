/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {CellContentType} from '@/api/generated/schema'
import {LETTER_COLUMNS, letterBlockOptions} from './letterBlockOptions'

const catalogue = {
    placeholders: [],
    labels: new Map([['member.firstName', 'Vorname']]),
    legal: false,
    choices: {groups: [], tags: []},
}

describe('the block editor of a letter', () => {
    it('offers texts and pictures in up to three columns, the logo and a visibility', () => {
        const options = letterBlockOptions(catalogue, true)

        expect(options.allowedKinds).toEqual([CellContentType.MARKDOWN, CellContentType.IMAGE])
        expect(options.maxColumns).toBe(LETTER_COLUMNS)
        expect(options.stationLogo).toBe(true)
        expect(options.restrictable).toBe(catalogue.choices)
        expect(options.markdownTools).toBeDefined()
    })

    it('shows placeholders as chips with their labels', () => {
        const prepared = letterBlockOptions(catalogue, false).tokens?.prepare('Hallo {{member.firstName}}')

        expect(prepared).toContain('>Vorname</span>')
    })
})
