/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {glyphFor, pieceGlyph} from './glyph'

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

/** A piece is drawn by its kind before its inventory, and plainly where neither chose a picture. */
describe('pieceGlyph', () => {
    it('takes the kind before the inventory', () => {
        expect(pieceGlyph({icon: 'chair', color: '#dc2626'}, {icon: 'tent', color: '#2563eb'}))
            .toEqual({icon: 'chair', color: '#dc2626'})
    })

    it('falls back to the inventory where the kind has nothing', () => {
        expect(pieceGlyph({icon: null, color: null}, {icon: 'tent', color: '#2563eb'}))
            .toEqual({icon: 'tent', color: '#2563eb'})
        expect(pieceGlyph(null, {icon: 'tent'}).icon).toEqual('tent')
    })

    it('draws a drawer of different things as a box when nobody chose a picture', () => {
        expect(pieceGlyph(null, {homogeneous: false}).icon).toEqual('box')
        expect(pieceGlyph(undefined, undefined).icon).toEqual('cube')
    })
})
