/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import ItemSearchPicker from './ItemSearchPicker.vue'
import type {InventoryItem} from '@/api/inventory'

/**
 * Which pieces the picker offers when a step asks which one arrived.
 *
 * <p>The wrong piece here takes somebody else's gear away or hands one body's property to another, and
 * both of those are found out at the shelf rather than on the screen. The filters are therefore the
 * subject: what is left out matters more than what is shown.
 */

const OURS = 11
const THEIRS = 12
const PROMISED = 13
const ON_A_MEMBER = 14
const LOST = 15
const ANOTHER_BODYS = 16

function piece(id: number, over: Partial<InventoryItem> = {}): InventoryItem {
    return {
        id,
        inventoryId: 1,
        internalId: `P-${id}`,
        name: `Handschuhe ${id}`,
        ownerKind: 'STATION',
        assignedTo: null,
        lostAt: null,
        ...over,
    } as InventoryItem
}

const ON_THEIR_SHELF = 17
const A_SHIRT = 18
const A_GAME = 19
const A_RADIO = 20

const ITEMS: InventoryItem[] = [
    piece(OURS),
    piece(THEIRS, {ownerKind: 'CLUSTER', ownerClusterId: 'body-1'}),
    piece(ON_THEIR_SHELF, {inventoryId: 2, ownerKind: 'CLUSTER', ownerClusterId: 'body-1'}),
    piece(A_SHIRT, {inventoryId: 3, name: 'T-Shirt'}),
    piece(A_GAME, {inventoryId: 4, name: 'Uno'}),
    piece(A_RADIO, {inventoryId: 5, name: 'Funkgerät blau'}),
    piece(PROMISED),
    piece(ON_A_MEMBER, {assignedTo: 4}),
    piece(LOST, {lostAt: '2026-01-01T00:00:00Z'}),
    piece(ANOTHER_BODYS, {ownerKind: 'CLUSTER', ownerClusterId: 'body-2'}),
]

vi.mock('@/api', () => ({
    inventory: {
        listAllItems: () => Promise.resolve(ITEMS),
        listInventories: () => Promise.resolve([
            {id: 1, name: 'Handschuhe', homogeneous: true, inventoryType: 'MIXED'},
            {id: 2, name: 'Vom Träger', homogeneous: true, inventoryType: 'EXTERNAL'},
            {id: 3, name: 'T-Shirt', homogeneous: true, inventoryType: 'INTERNAL'},
            {id: 4, name: 'Spiele', homogeneous: false, inventoryType: 'INTERNAL'},
            {id: 5, name: 'Handfunkgeräte', homogeneous: false, inventoryType: 'INTERNAL'},
        ]),
        listAllSizes: () => Promise.resolve([]),
    },
    inventoryArts: {listArts: () => Promise.resolve([])},
    inventoryContainers: {listContainers: () => Promise.resolve([])},
    stationMembers: {listMembers: () => Promise.resolve([])},
    movements: {
        listMovements: () => Promise.resolve([
            {id: 1, state: 'OPEN', incomingItemId: PROMISED},
            {id: 2, state: 'DONE', incomingItemId: OURS},
        ]),
    },
}))

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}},
})

async function offered(props: Record<string, unknown>): Promise<number[]> {
    const wrapper = mount(ItemSearchPicker, {
        global: {plugins: [i18n], stubs: {'font-awesome-icon': true, Spinner: true, ScanButton: true}},
        props,
    })
    await flushPromises()
    const picker = wrapper.findComponent({name: 'EntitySearchPicker'})
    const found = await (picker.props('searchFn') as (q: string) => Promise<InventoryItem[]>)('')
    return found.map(item => item.id)
}

describe('ItemSearchPicker', () => {
    it('offers everything it knows when nothing narrows it', async () => {
        expect(await offered({})).toEqual(expect.arrayContaining([OURS, THEIRS, ON_A_MEMBER, LOST]))
    })

    /** A replacement for the station's gear comes off the station's shelf, and never out of a body's. */
    it('offers only the same owner where a movement says whose gear it is about', async () => {
        const ours = await offered({ownerKind: 'STATION'})

        expect(ours).toContain(OURS)
        expect(ours, 'the association\'s gear is not the station\'s to hand over').not.toContain(THEIRS)
    })

    /** Two associations are two owners, and one of them is not the party to this movement. */
    it('offers only the association the movement is with', async () => {
        const theirs = await offered({ownerKind: 'CLUSTER', ownerClusterId: 'body-1'})

        expect(theirs).toContain(THEIRS)
        expect(theirs).not.toContain(ANOTHER_BODYS)
        expect(theirs).not.toContain(OURS)
    })

    /**
     * A shirt is replaced by a shirt.
     *
     * <p>Everything the station owns passed the owner filter, so a swap of a shirt offered board games,
     * radios and helmets: all of them the station's own, none of them a shirt. Naming the inventory is
     * what makes the offer about the same kind of thing.
     */
    it('offers only the shelf the movement is about', async () => {
        const shirts = await offered({inventoryId: 3, ownerKind: 'STATION', inventoryType: 'INTERNAL'})

        expect(shirts, 'the shirt shelf').toEqual([A_SHIRT])
        expect(shirts, 'and not the board games').not.toContain(A_GAME)
        expect(shirts, 'nor the radios').not.toContain(A_RADIO)
        expect(shirts, 'nor the gloves, which are the station\'s too').not.toContain(OURS)
    })

    /**
     * A shelf of the station's own gear is not where a replacement for the association's comes from,
     * and a shelf that holds both answers for either side.
     */
    it('offers only the same sort of shelf', async () => {
        const theirShelf = await offered({inventoryType: 'EXTERNAL'})

        expect(theirShelf, 'their own shelf, plainly').toContain(ON_THEIR_SHELF)
        expect(theirShelf, 'and the mixed one, which holds both sorts').toContain(THEIRS)

        const anyShelf = await offered({})
        expect(anyShelf, 'without a sort named, no shelf is ruled out').toContain(ON_THEIR_SHELF)
    })

    /**
     * A piece on a member, a piece reported missing and a piece another movement has already promised
     * are all unavailable, and only the last of the three looks free on the shelf.
     */
    it('leaves out what is not free to give away', async () => {
        const free = await offered({excludeAssigned: true, excludeLost: true, excludeSpokenFor: true})

        expect(free).toContain(OURS)
        expect(free, 'somebody is holding it').not.toContain(ON_A_MEMBER)
        expect(free, 'nobody knows where it is').not.toContain(LOST)
        expect(free, 'another movement is bringing it to somebody').not.toContain(PROMISED)
    })
})
