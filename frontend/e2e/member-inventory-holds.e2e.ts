/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * A list of gear says what is there, and nothing else.
 *
 * <p>For a member that means in their hands: a piece handed in for an exchange leaves their inventory
 * at that moment and a replacement joins it when it is handed over. For a station it means in the
 * house: a piece on its way to the body above is in no stock and no count. What is between the two is
 * a movement, and movements are read as movements.
 *
 * <p>These stories are the guard on both halves, and on the two things that follow from them: the
 * check counts a piece in an exchange as equipment the member has, and a member may take their own
 * exchange back while the piece is still on them.
 */

/** An inventory of the story's own, so chains bound to it are not shared with another story. */
async function ownGear(page: Page, headers: Record<string, string>, label: string, ownerKind = 'CLUSTER') {
    const stamp = `${label}-${Date.now()}-${Math.floor(performance.now())}`
    const made = await page.request.post('/api/v1/inventories',
        {headers, data: {name: `Bestand ${stamp}`, inventoryType: 'MIXED', hasSizes: false}})
    expect(made.ok(), 'the station keeps an inventory for this story').toBeTruthy()
    const inventoryId = (await made.json()).id
    const item = await addTo(page, headers, inventoryId, stamp, ownerKind)
    return {inventoryId, item}
}

async function addTo(
    page: Page,
    headers: Record<string, string>,
    inventoryId: number,
    label: string,
    ownerKind = 'CLUSTER',
) {
    const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
        headers,
        data: {internalId: `BE-${label}`, name: `Jacke ${label}`, sizeId: null, metadata: null,
            ownerKind, ownerClusterId: null},
    })
    expect(made.ok(), `a piece is recorded (${await made.text()})`).toBeTruthy()
    return made.json()
}

async function someMember(page: Page, headers: Record<string, string>) {
    const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
    const member = (Array.isArray(members) ? members : members.members ?? [])
        .find((m: {userType: string}) => m.userType === 'MEMBER')
    expect(member, 'the station has an ordinary member').toBeTruthy()
    return member
}

async function detail(page: Page, headers: Record<string, string>, id: number) {
    return page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
}

/** What the member's own inventory holds, which is the list under their name. */
async function heldBy(page: Page, headers: Record<string, string>, memberId: number): Promise<number[]> {
    const items = await page.request
        .get(`/api/v1/station-members/${memberId}/inventory-items`, {headers})
        .then(r => r.json())
    return items.map((item: {id: number}) => item.id)
}

/** The stock of an inventory, which is what the station reads on the inventory page. */
async function stockOf(page: Page, headers: Record<string, string>, inventoryId: number): Promise<number[]> {
    const items = await page.request
        .get(`/api/v1/inventories/${inventoryId}/items`, {headers})
        .then(r => r.json())
    return items.map((item: {id: number}) => item.id)
}

/** Starts an exchange for a piece the member is holding. */
async function askForExchange(page: Page, headers: Record<string, string>, item: {id: number},
                              inventoryId: number, memberId: number) {
    const started = await page.request.post('/api/v1/movements', {
        headers,
        data: {purpose: 'EXCHANGE', memberId, outgoingItemId: item.id, inventoryId, reason: 'Zu klein'},
    })
    expect(started.ok(), `starting answered ${await started.text()}`).toBeTruthy()
    return started.json()
}

/** Presses the step the movement stands on, as the station. */
async function press(page: Page, headers: Record<string, string>, id: number, pickedItemId: number | null = null) {
    const seen = await detail(page, headers, id)
    const step = seen.steps.find((one: {current: boolean}) => one.current)
    const done = await page.request.post(`/api/v1/movements/${id}/acknowledge`,
        {headers, data: {stepId: step.id, note: '', pickedItemId}})
    expect(done.ok(), `'${step.label}' was refused: ${await done.text()}`).toBeTruthy()
    return done.json()
}

test.describe('A list of gear shows what is there', () => {
    /**
     * ITM-42, ITM-44 - The piece leaves the member's list at the handover and the replacement joins it
     * at the next one.
     */
    test('the handover takes it off the member and the replacement only arrives at the end',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const {inventoryId, item} = await ownGear(page, headers, 'HAND')
            const spare = await addTo(page, headers, inventoryId, `HAND-S-${Date.now()}`)
            const member = await someMember(page, headers)
            await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
                {headers, data: {memberId: member.id, memberName: null}})

            const opened = await askForExchange(page, headers, item, inventoryId, member.id)
            expect(await heldBy(page, headers, member.id), 'asking for it changes nothing about who holds it')
                .toContain(item.id)

            await press(page, headers, opened.movement.id)
            expect(await heldBy(page, headers, member.id), 'handed in means off their list')
                .not.toContain(item.id)

            let walked = await detail(page, headers, opened.movement.id)
            for (let guard = 8; guard > 0 && walked.movement.state === 'OPEN'; guard -= 1) {
                const step = walked.steps.find((one: {current: boolean}) => one.current)
                if (!step?.actionable) break
                walked = await press(page, headers, opened.movement.id, step.picksItem ? spare.id : null)
                if (step.picksItem) {
                    expect(await heldBy(page, headers, member.id), 'and the replacement is not theirs yet')
                        .not.toContain(spare.id)
                }
            }

            const waiting = walked.steps.find((step: {current: boolean}) => step.current)
            if (waiting) {
                expect((await page.request.post(`/api/v1/movements/${opened.movement.id}/force`,
                    {headers, data: {stepId: waiting.id, note: 'An der Wache übergeben'}})).ok()).toBeTruthy()
            }

            expect(await heldBy(page, headers, member.id), 'once handed over the replacement is theirs')
                .toContain(spare.id)
        })

    /** ITM-45 - A piece they are still holding stays listed and says what is running on it. */
    test('a held piece with an exchange running stays in the list and names the step',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const {inventoryId, item} = await ownGear(page, headers, 'HELD')
            const member = await someMember(page, headers)
            await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
                {headers, data: {memberId: member.id, memberName: null}})

            await askForExchange(page, headers, item, inventoryId, member.id)

            const listed = await page.request
                .get(`/api/v1/station-members/${member.id}/inventory-items`, {headers})
                .then(r => r.json())
            const row = listed.find((entry: {id: number}) => entry.id === item.id)
            expect(row, 'it is still theirs').toBeTruthy()
            expect(row.movementStep, 'and the row says which step is waiting').toBeTruthy()
        })

    /** ITM-47 - What is in the post is in no stock of the station, while the row stays on the books. */
    test('a piece on its way to the owner is in no stock', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await ownGear(page, headers, 'POST')
        const member = await someMember(page, headers)
        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})

        const opened = await askForExchange(page, headers, item, inventoryId, member.id)
        await press(page, headers, opened.movement.id)
        expect(await stockOf(page, headers, inventoryId), 'at the station it is stock').toContain(item.id)

        await press(page, headers, opened.movement.id)
        const sent = await page.request
            .get(`/api/v1/inventory-items/${item.id}`, {headers})
            .then(r => r.json())
        expect(sent.custody, 'it is in the post').toBe('IN_TRANSIT')
        expect(await stockOf(page, headers, inventoryId), 'and out of the stock').not.toContain(item.id)
        expect(sent.id, 'while the row itself is still recorded').toBe(item.id)
    })

    /** ITM-49, ITM-50 - The member takes it back while they hold it, and not afterwards. */
    test('a member calls off their own exchange until they have handed the piece in',
        async ({managerPage: page, memberPage}) => {
            const headers = await apiHeaders(page)
            const memberHeaders = await apiHeaders(memberPage)
            const me = await memberPage.request.get('/api/v1/session', {headers: memberHeaders})
                .then(r => r.json())
            const {inventoryId, item} = await ownGear(page, headers, 'BACK')
            const second = await addTo(page, headers, inventoryId, `BACK-2-${Date.now()}`)
            for (const piece of [item, second]) {
                await page.request.put(`/api/v1/inventory-items/${piece.id}/assign`,
                    {headers, data: {memberId: me.member.id, memberName: null}})
            }

            const mine = await askForExchange(page, headers, item, inventoryId, me.member.id)
            const takenBack = await memberPage.request.post(`/api/v1/movements/${mine.movement.id}/cancel`,
                {headers: memberHeaders, data: {reason: 'Passt doch'}})
            expect(takenBack.ok(), `the member may take their own back (${await takenBack.text()})`).toBeTruthy()
            expect((await detail(page, headers, mine.movement.id)).movement.state).toBe('CANCELLED')
            expect(await heldBy(page, headers, me.member.id), 'and it never left them').toContain(item.id)

            const other = await askForExchange(page, headers, second, inventoryId, me.member.id)
            await press(page, headers, other.movement.id)
            const tooLate = await memberPage.request.post(`/api/v1/movements/${other.movement.id}/cancel`,
                {headers: memberHeaders, data: {reason: 'Doch nicht'}})
            expect(tooLate.status(), 'once handed in it is the station\'s to call off').toBe(403)
        })

    /** ITM-51 - Calling off during the post does not fetch the piece back. */
    test('calling off while it is in the post leaves it where it is', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await ownGear(page, headers, 'AWAY')
        const member = await someMember(page, headers)
        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})

        const opened = await askForExchange(page, headers, item, inventoryId, member.id)
        await press(page, headers, opened.movement.id)
        await press(page, headers, opened.movement.id)

        const called = await page.request.post(`/api/v1/movements/${opened.movement.id}/cancel`,
            {headers, data: {reason: 'Träger meldet sich nicht'}})
        expect(called.ok(), `calling off answered ${await called.text()}`).toBeTruthy()

        const after = await page.request
            .get(`/api/v1/inventory-items/${item.id}`, {headers})
            .then(r => r.json())
        expect(after.custody, 'it did not come home by being written off').not.toBe('WITH_MEMBER')
        expect(await heldBy(page, headers, member.id), 'and it is not back on the member either')
            .not.toContain(item.id)
    })

    /** ITM-52 - The check counts a piece in an exchange as equipment the member has. */
    test('the check sees no gap where an exchange is running', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await ownGear(page, headers, 'CHECK')
        const member = await someMember(page, headers)
        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})

        const wanted = await page.request.post('/api/v1/inventory-requirements', {
            headers,
            data: {inventoryId, userType: 'MEMBER', groupId: null, stationGroupId: null, quantity: 1},
        })
        expect(wanted.ok(), `a requirement is written (${await wanted.text()})`).toBeTruthy()

        const before = await requirementFor(page, headers, member.id, inventoryId)
        expect(before.assignedQuantity, 'the member is equipped').toBe(1)

        const opened = await askForExchange(page, headers, item, inventoryId, member.id)
        await press(page, headers, opened.movement.id)
        expect(await heldBy(page, headers, member.id), 'the piece has left their hands').not.toContain(item.id)

        const during = await requirementFor(page, headers, member.id, inventoryId)
        expect(during.assignedQuantity, 'and the check still counts them equipped').toBe(1)
        expect(during.inExchangeQuantity, 'saying that one of them is in an exchange').toBe(1)
    })
})

/** What the check says about one inventory for one member, read by opening the check on them. */
async function requirementFor(page: Page, headers: Record<string, string>, memberId: number, inventoryId: number) {
    const answer = await page.request.post(`/api/v1/inventory-checks/${memberId}/start`, {headers})
    expect(answer.ok(), `the check opens (${await answer.text()})`).toBeTruthy()
    const state = await answer.json()
    const row = (state.required ?? []).find((req: {inventoryId: number}) => req.inventoryId === inventoryId)
    expect(row, 'the requirement reaches the check').toBeTruthy()
    return row
}
