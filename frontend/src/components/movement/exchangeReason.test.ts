/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {InventorySize} from '@/api/generated/schema'
import {ExchangeReason, guessSize, reasonText, runsOffTheEnd, sizeRelation} from './exchangeReason'

function size(id: number, label: string, position: number): InventorySize {
    return {id, inventoryId: 1, label, position} as InventorySize
}

const SIZES = [size(1, 'S', 0), size(2, 'M', 1), size(3, 'L', 2)]

/** The size a swap asks for follows its reason along the inventory's own order of sizes. */
describe('exchangeReason', () => {
    it('asks one size up when too small, one down when too large and the same when broken', () => {
        expect(guessSize(ExchangeReason.TOO_SMALL, SIZES, 2, null)).toBe(3)
        expect(guessSize(ExchangeReason.TOO_LARGE, SIZES, 2, null)).toBe(1)
        expect(guessSize(ExchangeReason.BROKEN, SIZES, 2, 3)).toBe(2)
    })

    it('keeps the current size where the guess runs off the end, and says so', () => {
        expect(guessSize(ExchangeReason.TOO_SMALL, SIZES, 3, null)).toBe(3)
        expect(runsOffTheEnd(ExchangeReason.TOO_SMALL, SIZES, 3)).toBe(true)
        expect(guessSize(ExchangeReason.TOO_LARGE, SIZES, 1, null)).toBe(1)
        expect(runsOffTheEnd(ExchangeReason.TOO_LARGE, SIZES, 1)).toBe(true)
        expect(runsOffTheEnd(ExchangeReason.TOO_SMALL, SIZES, 2)).toBe(false)
    })

    it('leaves the chosen size alone for another reason and keeps a size the list does not know', () => {
        expect(guessSize(ExchangeReason.OTHER, SIZES, 2, 1)).toBe(1)
        expect(guessSize(ExchangeReason.TOO_SMALL, SIZES, null, null)).toBeNull()
        expect(guessSize(ExchangeReason.TOO_SMALL, [], 9, null)).toBe(9)
    })

    it('tells how the wanted size stands to the current one', () => {
        expect(sizeRelation(SIZES, 1, 3)).toBe('larger')
        expect(sizeRelation(SIZES, 3, 2)).toBe('smaller')
        expect(sizeRelation(SIZES, 2, 2)).toBe('same')
        expect(sizeRelation(SIZES, null, 2)).toBe('unknown')
    })

    it('writes the short name with the note, and another reason as its words alone', () => {
        expect(reasonText(ExchangeReason.TOO_SMALL, 'Zu klein', '')).toBe('Zu klein')
        expect(reasonText(ExchangeReason.BROKEN, 'Kaputt', ' Naht offen ')).toBe('Kaputt - Naht offen')
        expect(reasonText(ExchangeReason.OTHER, 'Anderer Grund', 'Farbe falsch')).toBe('Farbe falsch')
        expect(reasonText(ExchangeReason.OTHER, 'Anderer Grund', '  ')).toBe('')
        expect(reasonText(null, '', 'egal')).toBe('')
    })
})
