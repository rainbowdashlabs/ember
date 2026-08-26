/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * The chains a station is given, walked from the request at the front to the receipt at the back.
 *
 * These stories use the presets rather than chains of their own. That distinction is the whole point:
 * the existing movement stories each build a chain to suit themselves, so every one of them passed
 * while the chain a real station is handed stopped dead on the step that asks which piece arrived.
 * What is walked here is what a station actually gets.
 */

/** An inventory of the story's own, so two stories never walk each other's chain. */
async function gear(page: Page, headers: Record<string, string>, label: string, ownerKind = 'STATION') {
    const stamp = `${label}-${Date.now()}-${Math.floor(performance.now())}`
    const inventory = await page.request.post('/api/v1/inventories',
        {headers, data: {name: `Kettenprüfung ${stamp}`, inventoryType: 'MIXED', hasSizes: false}})
    expect(inventory.ok(), 'the station keeps an inventory for this story').toBeTruthy()
    const inventoryId = (await inventory.json()).id
    return {inventoryId, item: await addTo(page, headers, inventoryId, stamp, ownerKind)}
}

async function addTo(
    page: Page,
    headers: Record<string, string>,
    inventoryId: number,
    label: string,
    ownerKind = 'STATION',
) {
    const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
        headers,
        data: {internalId: `${label}-${Math.floor(Math.random() * 1e6)}`, name: 'Prüfstück',
            sizeId: null, metadata: null, ownerKind, ownerClusterId: null},
    })
    expect(made.ok(), 'the station records a piece').toBeTruthy()
    return made.json()
}

/**
 * One of the station's ordinary members, a different one per story.
 *
 * A story that asks a member for everything they hold reaches into whatever another story has just
 * assigned to them, so two stories sharing a member walk each other's chains. The index is how each
 * one keeps to itself.
 */
async function someMember(page: Page, headers: Record<string, string>, index = 0) {
    const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
    const ordinary = (Array.isArray(members) ? members : members.members ?? [])
        .filter((m: {userType: string}) => m.userType === 'MEMBER')
    expect(ordinary.length, 'the station has ordinary members to spread the stories over')
        .toBeGreaterThan(index)
    return ordinary[index]
}

async function detail(page: Page, headers: Record<string, string>, id: number) {
    return page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
}

/**
 * Presses whatever step this session may press, until the chain ends or it is somebody else's turn.
 *
 * The answer to a step that names the arriving piece comes from `arrival`, which either picks one
 * already recorded or writes down a new one, exactly as the screen offers.
 */
async function walk(
    page: Page,
    headers: Record<string, string>,
    id: number,
    arrival?: () => Promise<Record<string, unknown>>,
) {
    for (let guard = 12; guard > 0; guard -= 1) {
        const seen = await detail(page, headers, id)
        if (seen.movement.state !== 'OPEN') return seen
        const current = seen.steps.find((s: {current: boolean}) => s.current)
        if (!current?.actionable) return seen
        const extra = current.picksItem && arrival ? await arrival() : {}
        const stepped = await page.request.post(`/api/v1/movements/${id}/acknowledge`,
            {headers, data: {stepId: current.id, note: '', ...extra}})
        if (!stepped.ok()) {
            expect(stepped.ok(), `step '${current.label}' was refused: ${await stepped.text()}`).toBeTruthy()
            return detail(page, headers, id)
        }
    }
    return detail(page, headers, id)
}

test.describe('Movement chains', () => {
    /**
     * ITM-28 - Without an owner here, the station writes down what arrived.
     *
     * The reported fault and this story's reason to exist: the chain for a piece belonging to a body
     * outside Ember asks which piece came back, nothing in the station's stock is that piece, and the
     * movement could not be finished, forced or salvaged. The piece that left is gone for good, so its
     * row goes with it.
     */
    test('an exchange with an owner outside Ember is finished by recording what came', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'AUSSEN', 'CLUSTER')
        const member = await someMember(page, headers)

        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: item.id, inventoryId,
                reason: 'Zu klein'},
        })
        expect(started.ok(), await started.text()).toBeTruthy()
        const movementId = (await started.json()).movement.id

        const walked = await walk(page, headers, movementId,
            async () => ({newItem: {name: 'Ersatzstück', internalId: `NEU-${Date.now()}`}}))

        // The station walked its own steps and the owner's, and stopped at the one that is not its to
        // press: the member saying they have the replacement. Nobody is waiting on this story, so the
        // manager forces it with a note, which is what that step is for.
        expect(walked.movement.state, 'the chain waits for the member').toBe('OPEN')
        const theirs = walked.steps.find((s: {current: boolean}) => s.current)
        expect(theirs.actor).toBe('MEMBER')
        expect(theirs.label, 'and the chain ends with them confirming they have it').toBe('Erhalten')
        const forced = await page.request.post(`/api/v1/movements/${movementId}/force`,
            {headers, data: {stepId: theirs.id, note: 'Übergabe an der Wache bestätigt'}})
        expect(forced.ok(), await forced.text()).toBeTruthy()

        const gone = await page.request.get(`/api/v1/inventory-items/${item.id}`, {headers})
        expect(gone.status(), 'the piece that went to an owner we cannot see is off the books').toBe(404)

        const stock = await page.request.get(`/api/v1/inventories/${inventoryId}/items`, {headers})
            .then(r => r.json())
        expect(stock.some((row: {name: string}) => row.name === 'Ersatzstück'),
            'and what arrived was written down in its place').toBeTruthy()
    })


    /**
     * ITM-18 - The owner delivers into the store, and the piece is free afterwards.
     *
     * Ordered, sent, received. The last of those is the one that has to leave the piece somewhere it
     * can be used: a chain that ends with the gear still counted as in the post has stranded it, and
     * nothing else can pick it up again.
     */
    test('gear delivered into the store is usable afterwards', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'LIEFERUNG', 'CLUSTER')

        const started = await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'ISSUE', inventoryId, reason: 'Nachschub'}})
        expect(started.ok(), await started.text()).toBeTruthy()
        const opened = await started.json()
        expect(opened.steps.map((step: {label: string}) => step.label))
            .toEqual(['Bestellt', 'Verschickt', 'Erhalten'])

        const done = await walk(page, headers, opened.movement.id, async () => ({pickedItemId: item.id}))
        expect(done.movement.state).toBe('DONE')

        const arrived = await page.request.get(`/api/v1/inventory-items/${item.id}`, {headers})
            .then(r => r.json())
        expect(arrived.custody, 'it is at the station and not left in the post').toBe('AT_STATION')

        // And it can be moved again, which is what "not stranded" actually means.
        const again = await page.request.post('/api/v1/movements',
            {headers, data: {purpose: 'RETURN', outgoingItemId: item.id, inventoryId, reason: 'Doch nicht'}})
        expect(again.ok(), `the piece can start another chain (${await again.text()})`).toBeTruthy()
    })

    /**
     * ITM-21 - The list never reads backwards while a chain is walked.
     *
     * The owner confirming that the old piece reached it is progress, not a step back. Read off custody
     * alone it looked like one, because gear "with the owner" is the station's own shelf for the
     * station's own gear and the association's store for the association's. Reading both the same way
     * sent the row from shipped back to received, and the screen that walks by status could not get
     * past it.
     */
    test('an exchange of the owner\'s gear never reads backwards', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'HINAUS', 'CLUSTER')
        const spare = await addTo(page, headers, inventoryId, `HINAUS-E-${Date.now()}`, 'CLUSTER')
        const member = await someMember(page, headers, 4)

        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: item.id, inventoryId,
                reason: 'Zu klein'},
        })
        expect(started.ok(), await started.text()).toBeTruthy()
        const opened = await started.json()
        const seen: string[] = []

        for (let guard = 10; guard > 0; guard -= 1) {
            const now = await detail(page, headers, opened.movement.id)
            const listed = await page.request.get('/api/v1/exchanges', {headers}).then(r => r.json())
            const row = listed.find((entry: {id: number}) => entry.id === opened.movement.id)
            if (row) seen.push(row.status)
            if (now.movement.state !== 'OPEN') break
            const current = now.steps.find((step: {current: boolean}) => step.current)
            if (!current?.actionable) break
            await page.request.post(`/api/v1/movements/${opened.movement.id}/acknowledge`,
                {headers, data: {stepId: current.id, note: '', pickedItemId: current.picksItem ? spare.id : null}})
        }

        const order = ['ANNOUNCED', 'RECEIVED', 'SHIPPED', 'ARRIVED', 'DONE']
        const walkedBack = seen.some((status, i) => i > 0 && order.indexOf(status) < order.indexOf(seen[i - 1]!))
        expect(walkedBack, `the list went backwards: ${seen.join(' -> ')}`).toBeFalsy()

        expect(seen, 'the walk was long enough to show the owner leg').toContain('SHIPPED')
    })

    /**
     * ITM-29 - A spare from another inventory is offered.
     *
     * The other half of the same fault. The station owns the replacement, keeps it in a different
     * store, and the step that names it only ever looked in the one store the movement was about.
     */
    test('a replacement out of another inventory finishes an exchange', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'ZWEITLAGER')
        const elsewhere = await gear(page, headers, 'ERSATZLAGER')
        const member = await someMember(page, headers, 1)

        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: item.id, inventoryId,
                reason: 'Kaputt'},
        })
        expect(started.ok(), await started.text()).toBeTruthy()
        const movementId = (await started.json()).movement.id

        await walk(page, headers, movementId, async () => ({pickedItemId: elsewhere.item.id}))

        const replacement = await page.request
            .get(`/api/v1/inventory-items/${elsewhere.item.id}`, {headers})
            .then(r => r.json())
        expect(replacement.custody, 'the spare from the other store is with the member now').toBe('WITH_MEMBER')
    })

    /**
     * ITM-23 - Asking for a piece back is two steps.
     *
     * The shortest chain there is, and the shape every chain keeps: the station asks, the station has
     * it. Neither existed before, so a member handing back the station's own gear had no chain at all.
     */
    test('a member hands the station its own gear back in two steps', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'RUECK')
        const member = await someMember(page, headers, 2)

        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'RETURN', memberId: member.id, outgoingItemId: item.id, inventoryId, reason: 'Austritt'},
        })
        expect(started.ok(), await started.text()).toBeTruthy()
        const opened = await started.json()
        expect(opened.steps.length, 'a request and a receipt, and nothing in between').toBe(2)

        const done = await walk(page, headers, opened.movement.id)
        expect(done.movement.state).toBe('DONE')

        const back = await page.request.get(`/api/v1/inventory-items/${item.id}`, {headers}).then(r => r.json())
        expect(back.custody, 'and it is back in the station store').toBe('WITH_OWNER')
    })

    /**
     * ITM-37 - Everything back at once, each piece on its own chain.
     *
     * One press, and the pieces part ways: the station's own goes to its shelf, the association's into
     * the post. One movement for the lot would have to end in two places.
     */
    test('asking a member for everything starts one chain per piece', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const own = await gear(page, headers, 'SAMMEL')
        const foreign = await addTo(page, headers, own.inventoryId, `SAMMEL-F-${Date.now()}`, 'CLUSTER')
        const member = await someMember(page, headers, 3)

        for (const id of [own.item.id, foreign.id]) {
            await page.request.put(`/api/v1/inventory-items/${id}/assign`,
                {headers, data: {memberId: member.id, memberName: null}})
        }

        const asked = await page.request.post('/api/v1/movements/return-everything',
            {headers, data: {memberId: member.id}})
        expect(asked.ok(), await asked.text()).toBeTruthy()
        const started = await asked.json()

        const mine = started.filter((movement: {inventoryId: number}) => movement.inventoryId === own.inventoryId)
        expect(mine.length, 'one chain per piece the member held').toBeGreaterThanOrEqual(2)
        expect(started.every((movement: {purpose: string}) => movement.purpose === 'RETURN')).toBeTruthy()
    })

    /**
     * ITM-34 - The member confirms the receipt where their own gear is listed.
     *
     * Every chain now ends with the person holding the gear saying so. They could always press it and
     * never reach it: nothing on their own pages led to their chains.
     */
    test('a member confirms a receipt from their own equipment page',
        async ({managerPage: page, memberPage}) => {
            const headers = await apiHeaders(page)
            const {inventoryId, item} = await gear(page, headers, 'EMPFANG')

            const theirHeaders = await apiHeaders(memberPage)
            const session = await memberPage.request.get('/api/v1/session', {headers: theirHeaders})
                .then(r => r.json())
            const memberId = session.member.id

            const started = await page.request.post('/api/v1/movements', {
                headers,
                data: {purpose: 'ISSUE', memberId, inventoryId, reason: 'Erstausstattung'},
            })
            expect(started.ok(), await started.text()).toBeTruthy()
            const movementId = (await started.json()).movement.id
            await walk(page, headers, movementId, async () => ({pickedItemId: item.id}))

            const waiting = await detail(page, headers, movementId)
            expect(waiting.movement.state, 'the chain waits for the member to say they have it').toBe('OPEN')
            expect(waiting.steps.find((s: {current: boolean}) => s.current).actor).toBe('MEMBER')

            await memberPage.goto('/station/inventory/my')
            await expect(memberPage.getByTestId('my-movements')).toBeVisible({timeout: 15000})
            await memberPage.getByTestId('confirm-receipt').first().click()

            await expect
                .poll(async () => (await detail(page, headers, movementId)).movement.state, {timeout: 15000})
                .toBe('DONE')
        })

    /**
     * ITM-38 - A step is renamed and moved.
     *
     * Order is the whole of what a chain says. A step used to go to the end and stay there, so putting
     * a forgotten one in the middle meant writing everything after it again.
     */
    test('a step is renamed and put in another place', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const made = await page.request.post('/api/v1/movement-flows',
            {headers, data: {name: `Sortierprobe ${Date.now()}`, purpose: 'RETURN'}})
        const flowId = (await made.json()).id

        const first = await page.request.post(`/api/v1/movement-flows/${flowId}/steps`, {
            headers,
            data: {label: 'Angefordert', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'WITH_MEMBER', picksItem: false},
        }).then(r => r.json())
        const second = await page.request.post(`/api/v1/movement-flows/${flowId}/steps`, {
            headers,
            data: {label: 'Erhalten', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        }).then(r => r.json())

        expect((await page.request.put(`/api/v1/movement-flow-steps/${second.id}`, {
            headers,
            data: {label: 'Wieder im Lager', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        })).ok()).toBeTruthy()

        const ordered = await page.request.put(`/api/v1/movement-flows/${flowId}/step-order`,
            {headers, data: {stepIds: [second.id, first.id]}})
        expect(ordered.ok(), await ordered.text()).toBeTruthy()

        const flows = await page.request.get('/api/v1/movement-flows', {headers}).then(r => r.json())
        const mine = flows.find((flow: {id: number}) => flow.id === flowId)
        expect(mine.steps[0].id).toBe(second.id)
        expect(mine.steps[0].label, 'and it kept the new name').toBe('Wieder im Lager')
    })

    /**
     * ITM-39 - A chain that cannot be walked is refused.
     *
     * The editor says what is missing while somebody writes a chain, and binding one that would stop
     * halfway is refused outright rather than found out by the person holding the gear.
     */
    test('a chain that would strand the gear cannot be put to use', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const made = await page.request.post('/api/v1/movement-flows',
            {headers, data: {name: `Sackgasse ${Date.now()}`, purpose: 'RETURN'}})
        const flowId = (await made.json()).id
        await page.request.post(`/api/v1/movement-flows/${flowId}/steps`, {
            headers,
            data: {label: 'Abgeschickt', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'IN_TRANSIT', picksItem: false},
        })

        const flows = await page.request.get('/api/v1/movement-flows', {headers}).then(r => r.json())
        const mine = flows.find((flow: {id: number}) => flow.id === flowId)
        expect(mine.problem, 'the editor says what is missing').toBeTruthy()

        const bound = await page.request.put('/api/v1/movement-flow-bindings', {
            headers,
            data: {inventoryId: null, ownerKind: 'STATION', purpose: 'RETURN', party: 'MEMBER', flowId},
        })
        expect(bound.ok(), 'a chain of one step that ends in the post is not put to use').toBeFalsy()
    })
})
