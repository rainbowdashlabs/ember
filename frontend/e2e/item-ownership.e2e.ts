/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, accountWithout, pageAsThrowaway} from './fixtures/auth'

/**
 * Who owns a piece of gear, which is a different question from who has it.
 *
 * A station buys some of what it keeps and looks after the rest for the body above it, and the two are
 * told apart on the row rather than by which list they are in. What the station may do with each follows
 * from that one fact, so these stories are the ground the custody and movement ones stand on.
 */
test.describe('Item ownership', () => {
    /**
     * ITM-1 - An item says who owns it.
     *
     * Told apart without opening anything, because the difference decides what may be done with it.
     */
    test('an item says who owns it', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const items = await page.request
            .get('/api/v1/inventories/all-items', {headers})
            .then(r => r.json())

        const own = items.find((i: {ownerKind: string}) => i.ownerKind === 'STATION')
        const above = items.find((i: {ownerKind: string}) => i.ownerKind === 'CLUSTER')
        expect(own, 'the station bought some of it').toBeTruthy()
        expect(above, 'and keeps some for the body above it').toBeTruthy()

        await page.goto('/station/inventory/manage')
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * ITM-2 - One inventory holds both owners at once.
     *
     * A mixed inventory is the interesting case: the same list carries gear of both owners, and the row
     * says which without the reader having to know which inventory they are in.
     */
    test('one inventory holds gear of both owners', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const inventories = await page.request.get('/api/v1/inventories', {headers}).then(r => r.json())
        const mixed = inventories.find((i: {inventoryType: string}) => i.inventoryType === 'MIXED')
        expect(mixed, 'the demo keeps a mixed inventory').toBeTruthy()

        // Made mixed in fact as well as in kind: which owners the demo happens to have put in it is not
        // what this is about, and a mixed inventory that holds one owner today would still be mixed.
        for (const ownerKind of ['STATION', 'CLUSTER']) {
            const added = await page.request.post(`/api/v1/inventories/${mixed.id}/items`, {
                headers,
                data: {internalId: `MIX-${ownerKind}-${Date.now()}`, name: 'Beides', sizeId: null,
                    metadata: null, ownerKind, ownerClusterId: null},
            })
            expect(added.ok(), `a mixed inventory takes gear owned by the ${ownerKind}`).toBeTruthy()
        }

        const items = await page.request
            .get(`/api/v1/inventories/${mixed.id}/items`, {headers})
            .then(r => r.json())
        const owners = new Set(items.map((i: {ownerKind: string}) => i.ownerKind))
        expect(owners.size, 'and it holds gear of both owners at once').toBeGreaterThan(1)

        await page.goto(`/station/inventory/detail/${mixed.id}`)
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * ITM-3 - Gear whose owner is not on this instance stays editable here.
     *
     * Somebody has to be able to correct a name, and where the owner does not run here that somebody is
     * the station. The contrast is the cluster case, where the same fields are not the station's.
     */
    test('gear owned by a body that does not run here is still the station\'s to correct',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const inventories = await page.request.get('/api/v1/inventories', {headers}).then(r => r.json())
            const mixed = inventories.find((i: {inventoryType: string}) => i.inventoryType === 'MIXED')

            // Its own piece rather than one of the demo's: renaming is what this story is about, and a
            // piece somebody else's story is also holding would have two of them writing to one name.
            const made = await page.request.post(`/api/v1/inventories/${mixed.id}/items`, {
                headers,
                data: {internalId: `OFF-${Date.now()}`, name: 'Gemeindehelm', sizeId: null, metadata: null,
                    ownerKind: 'CLUSTER', ownerClusterId: null},
            })
            expect(made.ok(), 'the station records a piece it keeps for somebody off the instance').toBeTruthy()
            const offSystem = await made.json()

            const renamed = await page.request.put(`/api/v1/inventory-items/${offSystem.id}`, {
                headers,
                data: {internalId: offSystem.internalId, name: `${offSystem.name} korrigiert`,
                    sizeId: offSystem.sizeId ?? null, metadata: null,
                    ownerKind: 'CLUSTER', ownerClusterId: null},
            })
            expect(renamed.ok(), 'nobody else can, so the station may').toBeTruthy()

            const back = await page.request.put(`/api/v1/inventory-items/${offSystem.id}`, {
                headers,
                data: {internalId: offSystem.internalId, name: offSystem.name,
                    sizeId: offSystem.sizeId ?? null, metadata: null,
                    ownerKind: 'CLUSTER', ownerClusterId: null},
            })
            expect(back.ok()).toBeTruthy()
        })

    /**
     * ITM-4 - Recording gear the station does not own needs its own right.
     *
     * Writing down a piece that belongs to somebody else is a claim about somebody else's property, so it
     * is a separate right from recording what the station bought.
     */
    test('recording gear the station does not own needs its own right', async ({browser, request}) => {
        const account = await accountWithout(request, 'MEMBER', 'INVENTORY_CREATE_EXTERNAL', 'STATION_ADMINISTRATOR')
        const page = await pageAsThrowaway(browser, request, [], account)
        const headers = await apiHeaders(page)

        const inventories = await page.request.get('/api/v1/inventories', {headers})
        if (!inventories.ok()) {
            // Somebody without inventory rights at all cannot reach the list, which is the same refusal
            expect(inventories.status()).toBe(403)
            await page.context().close()
            return
        }
        const mixed = (await inventories.json())
            .find((i: {inventoryType: string}) => i.inventoryType === 'MIXED')

        const refused = await page.request.post(`/api/v1/inventories/${mixed.id}/items`, {
            headers,
            data: {internalId: `X-${Date.now()}`, name: 'Fremd', sizeId: null, metadata: null,
                ownerKind: 'CLUSTER', ownerClusterId: null},
        })
        expect(refused.ok(), 'without the right, gear somebody else owns cannot be written down').toBeFalsy()
        await page.context().close()
    })

    /**
     * ITM-5 - The inventory decides which owners may appear in it.
     *
     * An inventory of borrowed things holds nothing the station owns, and one of the station's own holds
     * nothing borrowed. A mixed one is the only place both may stand.
     */
    test('an inventory of borrowed things refuses gear the station owns', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const inventories = await page.request.get('/api/v1/inventories', {headers}).then(r => r.json())
        const borrowed = inventories.find((i: {inventoryType: string; hasSizes: boolean}) =>
            i.inventoryType === 'EXTERNAL' && !i.hasSizes)
        expect(borrowed, 'the demo keeps an inventory of borrowed things').toBeTruthy()

        const refused = await page.request.post(`/api/v1/inventories/${borrowed.id}/items`, {
            headers,
            data: {internalId: `OWN-${Date.now()}`, name: 'Eigenes', sizeId: null, metadata: null,
                ownerKind: 'STATION', ownerClusterId: null},
        })
        expect(refused.ok(), 'the station\'s own has no place in it').toBeFalsy()

        const accepted = await page.request.post(`/api/v1/inventories/${borrowed.id}/items`, {
            headers,
            data: {internalId: `BOR-${Date.now()}`, name: 'Geliehenes', sizeId: null, metadata: null,
                ownerKind: 'CLUSTER', ownerClusterId: null},
        })
        expect(accepted.ok(), 'while what it is for is recorded without argument').toBeTruthy()
    })

    /**
     * ITM-6 - Exchanging gear the station does not own does not make it the station's.
     *
     * The bug this whole rework is built on: an exchange in a mixed inventory used to put the returned
     * piece into the station's free stock whatever it said about its owner. The story exists to keep that
     * dead.
     */
    test('exchanging gear the station does not own leaves the owner alone', async ({managerPage: page, request}) => {
        const headers = await apiHeaders(page)
        const inventories = await page.request.get('/api/v1/inventories', {headers}).then(r => r.json())
        const mixed = inventories.find((i: {inventoryType: string}) => i.inventoryType === 'MIXED')

        const code = `EX-${Date.now()}`
        const made = await page.request.post(`/api/v1/inventories/${mixed.id}/items`, {
            headers,
            data: {internalId: code, name: 'Leihhelm', sizeId: null, metadata: null,
                ownerKind: 'CLUSTER', ownerClusterId: null},
        })
        expect(made.ok()).toBeTruthy()
        const item = await made.json()

        const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
        const member = (Array.isArray(members) ? members : members.members ?? [])
            .find((m: {userType: string}) => m.userType === 'MEMBER')
        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})

        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: item.id,
                inventoryId: mixed.id, reason: 'Zu klein'},
        })
        expect(started.ok()).toBeTruthy()
        const id = (await started.json()).movement.id

        // Walked to the end, whatever the chain is
        for (let guard = 8; guard > 0; guard -= 1) {
            const seen = await page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
            if (seen.movement.state !== 'OPEN') break
            const current = seen.steps.find((s: {current: boolean}) => s.current)
            if (!current?.actionable) break
            const replacement = current.picksItem
                ? await page.request
                    .get(`/api/v1/inventories/${mixed.id}/items`, {headers})
                    .then(r => r.json())
                    .then((items: {id: number; assignedTo: number | null; custody: string}[]) =>
                        items.find(i => i.id !== item.id && !i.assignedTo && i.custody === 'WITH_OWNER')?.id ?? null)
                : null
            const stepped = await page.request.post(`/api/v1/movements/${id}/acknowledge`,
                {headers, data: {stepId: current.id, note: '', pickedItemId: replacement}})
            if (!stepped.ok()) break
        }

        const after = await page.request
            .get(`/api/v1/inventory-items/${item.id}`, {headers})
            .then(r => r.json())
        expect(after.ownerKind, 'the piece still belongs to whoever owned it').toBe('CLUSTER')
    })
})
