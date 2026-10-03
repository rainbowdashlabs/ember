/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {CellContentType} from '@/api/generated/schema'
import {CHOOSER_CATEGORIES, chooserCategories} from './cellChoosers'

describe('chooser categories', () => {
    it('offer every kind where the editor names none', () => {
        const offered = chooserCategories().flatMap(category => category.items.map(item => item.type))
        const all = CHOOSER_CATEGORIES.flatMap(category => category.items.map(item => item.type))

        expect(offered).toEqual(all)
    })

    it('offer only the allowed kinds and leave empty categories out', () => {
        const categories = chooserCategories([CellContentType.MARKDOWN, CellContentType.IMAGE])

        expect(categories.map(category => category.key)).toEqual(['catBasic'])
        expect(categories[0]?.items.map(item => item.type)).toEqual([CellContentType.MARKDOWN, CellContentType.IMAGE])
    })
})
