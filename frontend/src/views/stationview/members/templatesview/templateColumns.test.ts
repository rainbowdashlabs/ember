/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {templateColumns} from './templateColumns'

const t = (key: string) => key

describe('templateColumns', () => {
    it('says which templates the association keeps where the list holds them', () => {
        const keys = templateColumns(t, true).map(column => column.key)
        expect(keys).toContain('ofAssociation')
        expect(keys.indexOf('ofAssociation')).toBe(keys.indexOf('kind') + 1)
    })

    it('leaves that out of the association\'s own list', () => {
        expect(templateColumns(t, false).map(column => column.key)).not.toContain('ofAssociation')
    })
})
