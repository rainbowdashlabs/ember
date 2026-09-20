/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {glyphFor} from './glyph'

describe('glyphFor', () => {
    it('takes the kind first, then the inventory', () => {
        expect(glyphFor({artIcon: 'walkie-talkie', inventoryIcon: 'box'}).icon).toEqual('walkie-talkie')
        expect(glyphFor({inventoryIcon: 'helmet-safety'}).icon).toEqual('helmet-safety')
    })

    it('reads an already resolved row as itself', () => {
        expect(glyphFor({icon: 'shirt', color: '#2563eb'})).toEqual({icon: 'shirt', color: '#2563eb'})
    })

    /**
     * The case that makes the pair one answer rather than two fields: somebody who picked a colour
     * and no shape meant their colour on the shape the inventory already had.
     */
    it('lets a kind carry a colour on the inventory shape', () => {
        expect(glyphFor({artColor: '#15803d', inventoryIcon: 'radio', inventoryColor: '#0f766e'})).toEqual({
            icon: 'radio',
            color: '#15803d',
        })
    })

    it('falls back to a cube for a stock and a box for a collection', () => {
        expect(glyphFor({homogeneous: true}).icon).toEqual('cube')
        expect(glyphFor({homogeneous: false}).icon).toEqual('box')
        expect(glyphFor({}).icon).toEqual('cube')
    })

    it('has no colour when nobody chose one', () => {
        expect(glyphFor({inventoryIcon: 'box'}).color).toBeNull()
        expect(glyphFor({inventoryColor: '   '}).color).toBeNull()
    })

    it('reads a blank name as nothing chosen', () => {
        expect(glyphFor({icon: '  ', artIcon: '', inventoryIcon: 'tent'}).icon).toEqual('tent')
    })
})
