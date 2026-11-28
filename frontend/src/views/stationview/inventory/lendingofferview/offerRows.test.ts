/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {OfferProduct} from '@/api/generated/schema'
import {choiceOf, choicesFor, matchesFilter, offerSummary, rowsOf} from './offerRows'

function product(overrides: Partial<OfferProduct> = {}): OfferProduct {
    return {
        inventoryId: 1, name: 'Spiele', description: '', pictureKey: null, icon: null, color: null, homogeneous: false,
        pieceCount: 2, share: null, offered: false, visible: null, kinds: [], pieces: [], ...overrides,
    }
}

/** How the offer page reads rules into choices, rows and filters. */
describe('offerRows', () => {
    it('reads a rule as the choice it stands for, per level', () => {
        expect(choiceOf(null, 'inventory')).toBe('none')
        expect(choiceOf(null, 'art')).toBe('inherit')
        expect(choiceOf({grant: 'GRANT', scope: 'ALL_PARTNERS', partnerIds: []}, 'item')).toBe('all')
        expect(choiceOf({grant: 'GRANT', scope: 'SPECIFIC', partnerIds: [3]}, 'art')).toBe('some')
        expect(choiceOf({grant: 'WITHHOLD', scope: 'ALL_PARTNERS', partnerIds: []}, 'art')).toBe('withheld')
        expect(choiceOf({grant: 'WITHHOLD', scope: 'ALL_PARTNERS', partnerIds: []}, 'inventory')).toBe('none')
        expect(choicesFor('inventory')).not.toContain('inherit')
    })

    it('lists each kind followed by its pieces, and pieces without a kind last', () => {
        const piece = (itemId: number, artId: number | null) => ({itemId, name: 'P', internalId: 'SP-1', artId, description: '', pictureKey: 'box', share: null, visible: true})
        const kind = (artId: number) => ({artId, name: 'K', description: '', pictureKey: null, pieceCount: 1, share: null, visible: null})
        const rows = rowsOf(product({kinds: [kind(5), kind(6)], pieces: [piece(9, 6), piece(8, 5), piece(7, null)]}))

        expect(rows.map(row => [row.key, row.depth])).toEqual([
            ['inventory:1', 0], ['art:5', 1], ['item:8', 2], ['art:6', 1], ['item:9', 2], ['item:7', 1],
        ])
        expect(rows[2]).toMatchObject({detail: 'SP-1', pictureKey: 'box', visible: true})
    })

    it('takes a product as offered where the server resolved it so, and counts what lacks a text or photo', () => {
        const offeredByPiece = product({offered: true})
        const described = product({inventoryId: 2, description: 'Text', pictureKey: 'p', offered: true})
        const hidden = product({inventoryId: 3})

        expect([offeredByPiece, described, hidden].filter(p => matchesFilter(p, 'offered')).map(p => p.inventoryId)).toEqual([1, 2])
        expect(matchesFilter(hidden, 'notOffered')).toBe(true)
        expect(matchesFilter(described, 'noDescription')).toBe(false)
        expect(offerSummary([offeredByPiece, described, hidden])).toEqual({offered: 2, noDescription: 2, noPicture: 2})
    })
})
