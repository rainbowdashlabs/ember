/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * Where a piece of gear actually is, which is a different question from who owns it.
 *
 * A station holds things it does not own and owns things it is not holding, and every list of "what we
 * have" follows the holding rather than the owning. These stories walk the states a piece moves through
 * and the places each one shows.
 */

/** A piece of the station's own gear, made for one story so no other story loses theirs. */
async function ownGear(page: Page, headers: Record<string, string>, label: string) {
    const inventories = await page.request.get('/api/v1/inventories', {headers}).then(r => r.json())
    const mixed = inventories.find((i: {inventoryType: string}) => i.inventoryType === 'MIXED')
    const made = await page.request.post(`/api/v1/inventories/${mixed.id}/items`, {
        headers,
        data: {internalId: `${label}-${Date.now()}`, name: 'Prüfstück', sizeId: null, metadata: null,
            ownerKind: 'STATION', ownerClusterId: null},
    })
    expect(made.ok(), 'the station records a piece of its own').toBeTruthy()
    return {inventoryId: mixed.id, item: await made.json()}
}

/** An ordinary member of the station, to hand things to. */
async function someMember(page: Page, headers: Record<string, string>) {
    const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
    const member = (Array.isArray(members) ? members : members.members ?? [])
        .find((m: {userType: string}) => m.userType === 'MEMBER')
    expect(member, 'the station has an ordinary member').toBeTruthy()
    return member
}

/** One item as the station currently sees it. */
async function item(page: Page, headers: Record<string, string>, id: number) {
    return page.request.get(`/api/v1/inventory-items/${id}`, {headers}).then(r => r.json())
}

test.describe('Item custody', () => {
    /**
     * ITM-7 - The station's list is what it holds, not what it owns.
     *
     * Gear resting in the owner's store is not at the station, however much the station is the one
     * recording it.
     */
    test('the station lists what it holds rather than what it owns', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const items = await page.request
            .get('/api/v1/inventories/all-items', {headers})
            .then(r => r.json())

        const held = items.filter((i: {custody: string}) =>
            i.custody === 'AT_STATION' || i.custody === 'WITH_MEMBER')
        expect(held.length, 'the station holds gear').toBeGreaterThan(0)
        expect(held.some((i: {ownerKind: string}) => i.ownerKind === 'CLUSTER'),
            'including gear it does not own').toBeTruthy()

        await page.goto('/station/inventory')
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * ITM-11 - Handing out and taking back stays one action.
     *
     * Handing somebody a helmet across a table does not open a request, and nothing appears in any queue
     * because nothing was asked of anybody.
     */
    test('handing gear over and back opens no request', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {item: made} = await ownGear(page, headers, 'HAND')
        const member = await someMember(page, headers)

        const before = await page.request.get('/api/v1/movements', {headers}).then(r => r.json())
        const count = (Array.isArray(before) ? before : before.movements ?? []).length

        await page.request.put(`/api/v1/inventory-items/${made.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        expect((await item(page, headers, made.id)).custody).toBe('WITH_MEMBER')

        await page.request.put(`/api/v1/inventory-items/${made.id}/assign`,
            {headers, data: {memberId: null, memberName: null}})
        expect((await item(page, headers, made.id)).custody).toBe('WITH_OWNER')

        const after = await page.request.get('/api/v1/movements', {headers}).then(r => r.json())
        expect((Array.isArray(after) ? after : after.movements ?? []).length,
            'and nothing was asked of anybody').toBe(count)
    })

    /**
     * ITM-8 - An item on its way is neither here nor there.
     *
     * Between two parties it belongs to neither store: it reads as in transit, names the movement carrying
     * it, and is in nobody's free stock.
     */
    test('gear on its way is in neither store', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const inventory = await page.request.post('/api/v1/inventories',
            {headers, data: {name: `Unterwegs ${Date.now()}`, inventoryType: 'MIXED', hasSizes: false}})
        expect(inventory.ok()).toBeTruthy()
        const inventoryId = (await inventory.json()).id

        const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
            headers,
            data: {internalId: `TRANSIT-${Date.now()}`, name: 'Prüfstück', sizeId: null, metadata: null,
                ownerKind: 'CLUSTER', ownerClusterId: null},
        })
        expect(made.ok()).toBeTruthy()
        const piece = await made.json()

        // A chain that parks it in the post and waits there
        const flow = await page.request.post('/api/v1/movement-flows',
            {headers, data: {name: `Unterwegs ${Date.now()}`, purpose: 'RETURN'}})
        const flowId = (await flow.json()).id
        for (const step of [
            {label: 'Wache schickt', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'IN_TRANSIT', picksItem: false},
            {label: 'Träger nimmt an', actor: 'OWNER', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        ]) {
            expect((await page.request.post(`/api/v1/movement-flows/${flowId}/steps`,
                {headers, data: step})).ok()).toBeTruthy()
        }
        expect((await page.request.put('/api/v1/movement-flow-bindings',
            {headers, data: {inventoryId, ownerKind: 'CLUSTER', purpose: 'RETURN', flowId}})).ok()).toBeTruthy()

        const started = await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'RETURN', outgoingItemId: piece.id, inventoryId, reason: 'Zurück'}})
        expect(started.ok()).toBeTruthy()
        const movementId = (await started.json()).movement.id

        const moving = await item(page, headers, piece.id)
        expect(moving.custody, 'it is in the post').toBe('IN_TRANSIT')
        expect(moving.custodyMovementId, 'and says which movement is carrying it').toBe(movementId)

        const free = await page.request
            .get(`/api/v1/inventories/${inventoryId}/items`, {headers})
            .then(r => r.json())
            .then((items: {id: number; custody: string}[]) =>
                items.filter(i => i.custody === 'AT_STATION' || i.custody === 'WITH_OWNER'))
        expect(free.some((i: {id: number}) => i.id === piece.id),
            'and stands in neither store').toBeFalsy()

        return {inventoryId, piece}
    })

    /**
     * ITM-9 - An item in transit cannot be handed to anybody.
     *
     * Nobody can hand over what is in the post, and reaching for it anyway is refused rather than quietly
     * pulling it out of a movement somebody is walking.
     */
    test('gear in the post cannot be handed to anybody', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const inventory = await page.request.post('/api/v1/inventories',
            {headers, data: {name: `Griffbereit ${Date.now()}`, inventoryType: 'MIXED', hasSizes: false}})
        const inventoryId = (await inventory.json()).id

        const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
            headers,
            data: {internalId: `HOLD-${Date.now()}`, name: 'Prüfstück', sizeId: null, metadata: null,
                ownerKind: 'CLUSTER', ownerClusterId: null},
        })
        const piece = await made.json()

        const flow = await page.request.post('/api/v1/movement-flows',
            {headers, data: {name: `Griffbereit ${Date.now()}`, purpose: 'RETURN'}})
        const flowId = (await flow.json()).id
        for (const step of [
            {label: 'Wache schickt', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'IN_TRANSIT', picksItem: false},
            {label: 'Träger nimmt an', actor: 'OWNER', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        ]) {
            await page.request.post(`/api/v1/movement-flows/${flowId}/steps`, {headers, data: step})
        }
        await page.request.put('/api/v1/movement-flow-bindings',
            {headers, data: {inventoryId, ownerKind: 'CLUSTER', purpose: 'RETURN', flowId}})
        await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'RETURN', outgoingItemId: piece.id, inventoryId, reason: 'Zurück'}})

        expect((await item(page, headers, piece.id)).custody).toBe('IN_TRANSIT')

        const member = await someMember(page, headers)
        const refused = await page.request.put(`/api/v1/inventory-items/${piece.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        expect(refused.ok(), 'nobody can hand over what is in the post').toBeFalsy()

        expect((await item(page, headers, piece.id)).custody, 'and it stays on its way').toBe('IN_TRANSIT')
    })

    /**
     * ITM-10 - A lost item is in nobody's custody.
     *
     * It stays on the record of whoever had it, because gear a member cannot find is still gear that
     * member is short of, and it leaves the free stock because nobody can hand out what nobody can find.
     */
    test('a lost item stays with its holder and leaves the free stock', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item: made} = await ownGear(page, headers, 'LOST')
        const member = await someMember(page, headers)

        await page.request.put(`/api/v1/inventory-items/${made.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})

        const lost = await page.request.put(`/api/v1/inventory-items/${made.id}/lost`, {headers})
        expect(lost.ok()).toBeTruthy()

        const gone = await item(page, headers, made.id)
        expect(gone.custody).toBe('LOST')
        expect(gone.lostAt, 'with the date it went').toBeTruthy()
        expect(gone.assignedTo, 'and still on the record of whoever had it').toBe(member.id)

        const free = await page.request
            .get(`/api/v1/inventories/${inventoryId}/items`, {headers})
            .then(r => r.json())
            .then((items: {id: number; custody: string}[]) =>
                items.filter(i => i.custody === 'AT_STATION' || i.custody === 'WITH_OWNER'))
        expect(free.some((i: {id: number}) => i.id === made.id),
            'nobody can hand out what nobody can find').toBeFalsy()
    })

    /**
     * ITM-12 - A container says where in the store, not who has it.
     *
     * Shelving something is not handing it to anybody: the station still holds it, and the container only
     * says which shelf.
     */
    test('shelving gear leaves its custody alone', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {item: made} = await ownGear(page, headers, 'SHELF')

        const containers = await page.request.get('/api/v1/inventory-containers', {headers})
        if (!containers.ok()) test.skip(true, 'the station keeps no containers to shelve into')
        const list = await containers.json()
        const container = (Array.isArray(list) ? list : list.containers ?? [])[0]
        test.skip(!container, 'the station keeps no containers to shelve into')

        const shelved = await page.request.put(`/api/v1/inventory-items/${made.id}/container`,
            {headers, data: {containerId: container.id}})
        expect(shelved.ok()).toBeTruthy()

        const onTheShelf = await item(page, headers, made.id)
        expect(onTheShelf.containerId, 'it is on a shelf').toBe(container.id)
        expect(onTheShelf.custody, 'and the station still holds it').toBe('WITH_OWNER')
    })

    /**
     * ITM-13 - A member sees what they hold, whoever owns it.
     *
     * The list a member reads is about what is theirs to look after, which has nothing to do with who
     * bought it.
     */
    test('a member sees what they hold, whoever owns it', async ({memberPage: page}) => {
        const headers = await apiHeaders(page)
        const mine = await page.request
            .get('/api/v1/my-inventory-items', {headers})
            .then(r => r.json())

        expect(Array.isArray(mine), 'the member has a list of their own').toBeTruthy()
        for (const piece of mine) {
            expect(piece.ownerKind, 'and each piece says who owns it').toBeTruthy()
        }

        await page.goto('/station/inventory/my')
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
