/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {copyNotices} from './useTemplateDuplication'

const t = (key: string, named?: Record<string, unknown>) => `${key} ${JSON.stringify(named ?? {})}`

describe('copyNotices', () => {
    it('says nothing about a copy that prints as the original did', () => {
        expect(copyNotices({fontsOutOfReach: [], picturesOutOfReach: 0}, t)).toEqual([])
    })

    it('names the fonts out of reach and counts the missing pictures', () => {
        expect(copyNotices({fontsOutOfReach: ['Hausschrift', 'Wappen Sans'], picturesOutOfReach: 2}, t)).toEqual([
            'documentTemplates.copyFontsOutOfReach {"fonts":"Hausschrift, Wappen Sans"}',
            'documentTemplates.copyPicturesOutOfReach {"count":2}',
        ])
    })
})
