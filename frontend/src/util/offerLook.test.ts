/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {AvailableInventoryEntry} from '@/api/generated/schema'
import {offerLook} from './offerLook'

function offer(over: Partial<AvailableInventoryEntry>): AvailableInventoryEntry {
    return {
        inventoryId: 1, inventoryName: 'Zelte', artId: null, artName: null, stationId: 'nord', stationName: 'Nord',
        availableCount: 2, distanceKm: null, pictureKey: null, icon: null, color: null, homogeneous: true, ...over,
    } as AvailableInventoryEntry
}

/** A partner's offer is drawn with the photo and icon the partner resolved for it. */
describe('offerLook', () => {
    it('asks this instance for the partner photo by station and key', () => {
        expect(offerLook(offer({pictureKey: 'abc'})).pictureSrc).toContain('/federated/lending/pictures/nord/abc?size=64')
    })

    it('draws the icon, or the collection shape where there is none', () => {
        expect(offerLook(offer({icon: 'tent'})).glyph.icon).toBe('tent')
        expect(offerLook(offer({homogeneous: false})).glyph.icon).toBe('box')
        expect(offerLook(offer({})).pictureSrc).toBeNull()
    })
})
