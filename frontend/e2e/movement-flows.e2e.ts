/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders, accountWithout, pageAsThrowaway} from './fixtures/auth'

/**
 * The chains themselves: how a station shapes what a movement walks, and what it may not reshape while
 * somebody is halfway along one.
 *
 * Each story builds its own inventory and its own chain. A chain is bound to the pair of an inventory and
 * an owner, so two stories binding to the same inventory would each be walking the other's.
 */

/** An inventory of this story's own, with one piece in it. */
async function ownGround(page: Page, headers: Record<string, string>, label: string, ownerKind = 'CLUSTER') {
    const stamp = `${label}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
    const inventory = await page.request.post('/api/v1/inventories',
        {headers, data: {name: `Prüfinventar ${stamp}`, inventoryType: 'MIXED', hasSizes: false}})
    expect(inventory.ok()).toBeTruthy()
    const inventoryId = (await inventory.json()).id

    const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
        headers,
        data: {internalId: stamp, name: 'Prüfstück', sizeId: null, metadata: null,
            ownerKind, ownerClusterId: null},
    })
    expect(made.ok()).toBeTruthy()
    return {inventoryId, item: await made.json(), stamp}
}

/** A chain of the given steps, bound to that inventory for that owner and purpose. */
async function chain(
    page: Page,
    headers: Record<string, string>,
    inventoryId: number,
    name: string,
    steps: Record<string, unknown>[],
    purpose = 'RETURN',
    ownerKind = 'CLUSTER',
) {
    const flow = await page.request.post('/api/v1/movement-flows', {headers, data: {name, purpose}})
    expect(flow.ok()).toBeTruthy()
    const flowId = (await flow.json()).id

    const stepIds: number[] = []
    for (const step of steps) {
        const added = await page.request.post(`/api/v1/movement-flows/${flowId}/steps`, {headers, data: step})
        expect(added.ok()).toBeTruthy()
        stepIds.push((await added.json()).id)
    }

    const bound = await page.request.put('/api/v1/movement-flow-bindings',
        {headers, data: {inventoryId, ownerKind, purpose, flowId}})
    expect(bound.ok()).toBeTruthy()
    return {flowId, stepIds}
}

const SENDS = {label: 'Wache schickt', actor: 'STATION', subject: 'OUTGOING',
    custodyAfter: 'IN_TRANSIT', picksItem: false}
const ARRIVES = {label: 'Träger nimmt an', actor: 'OWNER', subject: 'OUTGOING',
    custodyAfter: 'WITH_OWNER', picksItem: false}
const FILED = {label: 'Träger legt weg', actor: 'OWNER', subject: 'OUTGOING',
    custodyAfter: 'WITH_OWNER', picksItem: false}

test.describe('Movement flows', () => {
    /**
     * ITM-22 - A flow is edited and the next movement walks the new one.
     *
     * A chain is read when a movement starts, so adding a step changes what starts afterwards and leaves
     * what is already walking alone. A chain is also not reshaped under somebody who is walking it, so
     * the first movement is finished before the step is added.
     */
    test('a movement started afterwards walks the added step', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await ownGround(page, headers, 'EDIT')
        const {flowId} = await chain(page, headers, inventoryId, `Kurz ${Date.now()}`, [SENDS, ARRIVES])

        const before = await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'RETURN', outgoingItemId: item.id, inventoryId, reason: 'Erst'}})
        expect(before.ok()).toBeTruthy()
        const early = await before.json()
        expect(early.steps.length).toBe(2)

        const waiting = early.steps.find((step: {current: boolean}) => step.current)
        expect((await page.request.post(`/api/v1/movements/${early.movement.id}/acknowledge`,
            {headers, data: {stepId: waiting.id, note: ''}})).ok()).toBeTruthy()

        const added = await page.request.post(`/api/v1/movement-flows/${flowId}/steps`,
            {headers, data: FILED})
        expect(added.ok(), await added.text()).toBeTruthy()

        const second = (await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
            headers,
            data: {internalId: `EDIT2-${Date.now()}`, name: 'Prüfstück', sizeId: null, metadata: null,
                ownerKind: 'CLUSTER', ownerClusterId: null},
        })).json()
        const after = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'RETURN', outgoingItemId: (await second).id, inventoryId, reason: 'Danach'},
        })
        expect(after.ok()).toBeTruthy()
        expect((await after.json()).steps.length, 'the one started afterwards carries the added step').toBe(3)

        const untouched = await page.request
            .get(`/api/v1/movements/${early.movement.id}`, {headers})
            .then(r => r.json())
        expect(untouched.steps.length, 'and the one already walking does not').toBe(2)
    })

    /**
     * ITM-23 - Reordering is refused while a movement is walking the flow.
     *
     * Moving the ground under somebody halfway along a chain would leave them on a step that is no longer
     * where they are, so it is refused while one is open.
     */
    test('reshaping is refused while a movement is walking it', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await ownGround(page, headers, 'BUSY')
        const {stepIds} = await chain(page, headers, inventoryId, `Beschäftigt ${Date.now()}`,
            [SENDS, ARRIVES, FILED])

        const started = await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'RETURN', outgoingItemId: item.id, inventoryId, reason: 'Läuft'}})
        expect(started.ok()).toBeTruthy()
        const id = (await started.json()).movement.id

        const refused = await page.request.delete(`/api/v1/movement-flow-steps/${stepIds[1]}`, {headers})
        expect(refused.ok(), 'a step somebody is walking towards cannot be taken out').toBeFalsy()

        // Once the chain has closed, the same change is allowed
        const current = (await page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json()))
            .steps.find((s: {current: boolean}) => s.current)
        await page.request.post(`/api/v1/movements/${id}/acknowledge`,
            {headers, data: {stepId: current.id, note: ''}})
        const walked = await page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
        const last = walked.steps.find((s: {current: boolean}) => s.current)
        if (last) {
            await page.request.post(`/api/v1/movements/${id}/acknowledge`,
                {headers, data: {stepId: last.id, note: ''}})
        }

        const allowed = await page.request.delete(`/api/v1/movement-flow-steps/${stepIds[1]}`, {headers})
        expect(allowed.ok(), 'with nothing walking it, the chain is the station\'s to reshape').toBeTruthy()
    })

    /**
     * ITM-24 - A step in use is archived, never deleted.
     *
     * It leaves the chains that start afterwards and stays in the history of everything that passed it,
     * because a record of what happened cannot lose a step to a later edit.
     */
    test('a step that has been walked is kept in the history it belongs to', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await ownGround(page, headers, 'ARCH')
        const {stepIds} = await chain(page, headers, inventoryId, `Archiv ${Date.now()}`, [SENDS, ARRIVES, FILED])

        const started = await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'RETURN', outgoingItemId: item.id, inventoryId, reason: 'Läuft'}})
        const id = (await started.json()).movement.id

        for (let guard = 4; guard > 0; guard -= 1) {
            const seen = await page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
            if (seen.movement.state !== 'OPEN') break
            const current = seen.steps.find((s: {current: boolean}) => s.current)
            if (!current?.actionable) break
            await page.request.post(`/api/v1/movements/${id}/acknowledge`,
                {headers, data: {stepId: current.id, note: ''}})
        }

        const removed = await page.request.delete(`/api/v1/movement-flow-steps/${stepIds[0]}`, {headers})
        expect(removed.ok()).toBeTruthy()

        const history = await page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
        expect(history.steps.some((s: {id: number}) => s.id === stepIds[0]),
            'the movement that walked it still shows it').toBeTruthy()

        const next = await page.request.get('/api/v1/movement-flows', {headers}).then(r => r.json())
        const live = next.flatMap((f: {steps: {id: number; archived: boolean}[]}) => f.steps ?? [])
            .filter((s: {id: number; archived: boolean}) => s.id === stepIds[0] && !s.archived)
        expect(live, 'while nothing starting now walks it any more').toEqual([])
    })

    /**
     * ITM-25 - Which flow applies follows the item's owner.
     *
     * Two pieces in one inventory, owned differently, walk different chains. That is the whole point of
     * the rework: the item says who owns it, and the inventory no longer stands in for that.
     */
    test('two owners in one inventory walk different chains', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item: borrowed} = await ownGround(page, headers, 'OWNERS')

        const own = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
            headers,
            data: {internalId: `OWNERS-OWN-${Date.now()}`, name: 'Eigenes', sizeId: null, metadata: null,
                ownerKind: 'STATION', ownerClusterId: null},
        }).then(r => r.json())

        await chain(page, headers, inventoryId, `Träger ${Date.now()}`, [SENDS, ARRIVES, FILED])
        await chain(page, headers, inventoryId, `Wache ${Date.now()}`,
            [{label: 'Rückgabe angefordert', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'AT_STATION', picksItem: false},
             {label: 'Wache legt zurück', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false}], 'RETURN', 'STATION')

        const forBorrowed = await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'RETURN', outgoingItemId: borrowed.id, inventoryId, reason: 'Zurück'}})
        const forOwn = await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'RETURN', outgoingItemId: own.id, inventoryId, reason: 'Zurück'}})
        expect(forBorrowed.ok() && forOwn.ok()).toBeTruthy()

        expect((await forBorrowed.json()).steps.length, 'the borrowed piece walks the longer chain').toBe(3)
        expect((await forOwn.json()).steps.length, 'and the station\'s own the shorter one').toBe(2)
    })

    /**
     * ITM-26 - The flow editor is closed to the people who only use it.
     *
     * Shaping the chains is a manager's job. Somebody who works the queue walks them and does not rewrite
     * them underneath everybody else.
     */
    test('shaping the chains is closed to somebody who only walks them', async ({browser, request}) => {
        const account = await accountWithout(request, 'MEMBER', 'INVENTORY_MANAGER', 'STATION_ADMINISTRATOR')
        const page = await pageAsThrowaway(browser, request, [], account)
        const headers = await apiHeaders(page)

        const refused = await page.request.post('/api/v1/movement-flows',
            {headers, data: {name: 'Heimlich', purpose: 'RETURN'}})
        expect(refused.ok(), 'a chain is not theirs to make').toBeFalsy()

        const bound = await page.request.put('/api/v1/movement-flow-bindings',
            {headers, data: {inventoryId: null, ownerKind: 'STATION', purpose: 'RETURN', flowId: 1}})
        expect(bound.ok(), 'nor to point at a different one').toBeFalsy()

        await page.context().close()
    })
})
