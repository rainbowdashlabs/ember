/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {InventoryItem} from '@/api/generated/schema'
import {isWithinReach, passesItemFilters} from './itemFilters'

function piece(over: Partial<InventoryItem>): InventoryItem {
    return {
        id: 1,
        inventoryId: 1,
        custody: 'WITH_OWNER',
        ownerKind: 'STATION',
        assignedTo: null,
        lostAt: null,
        ...over,
    } as InventoryItem
}

const context = {inventoryTypeOf: () => undefined, spokenFor: new Set<number>()}

describe('isWithinReach', () => {
    it('takes what is on the shelf, at the station or with a member', () => {
        expect(isWithinReach(piece({custody: 'WITH_OWNER', ownerKind: 'STATION'})), 'the station\'s own shelf').toBe(true)
        expect(isWithinReach(piece({custody: 'AT_STATION', ownerKind: 'CLUSTER'}))).toBe(true)
        expect(isWithinReach(piece({custody: 'WITH_MEMBER', assignedTo: 4}))).toBe(true)
    })

    /** A piece out of reach is refused when it is handed out, so a picker for a hand-out leaves it out. */
    it('leaves out what is away or missing', () => {
        expect(isWithinReach(piece({custody: 'WITH_PARTNER'})), 'with a partner').toBe(false)
        expect(isWithinReach(piece({custody: 'IN_TRANSIT'})), 'in the post').toBe(false)
        expect(isWithinReach(piece({custody: 'WITH_OWNER', ownerKind: 'CLUSTER'})), 'back with the association').toBe(false)
        expect(isWithinReach(piece({custody: 'LOST', lostAt: '2026-01-01T00:00:00Z'})), 'missing').toBe(false)
    })
})

describe('passesItemFilters', () => {
    it('drops pieces out of reach only where the caller asks for it', () => {
        const lent = piece({custody: 'WITH_PARTNER'})

        expect(passesItemFilters(lent, {}, context)).toBe(true)
        expect(passesItemFilters(lent, {excludeUnavailable: true}, context)).toBe(false)
    })
})
