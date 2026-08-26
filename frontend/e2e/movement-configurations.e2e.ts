/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Browser, Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'

/**
 * The same two exchanges, walked in each of the three shapes a station can have.
 *
 * Which chain a movement walks depends on whether a body above the station exists and whether it
 * keeps its gear here, and the three answers give three different chains with different people
 * pressing them. Walking only the first of them is how the chain for gear belonging to a body outside
 * Ember came to be one nobody could finish: it was never walked end to end by anything.
 *
 * Each configuration walks its own gear and the owner's, and the run ends where the chain says it
 * ends, with both pieces where the chain put them.
 */

interface Setup {
    /** What this configuration is called when a failure names it. */
    label: string
    page: Page
    headers: Record<string, string>
    /** The member the exchange is for, who is also the one running the station in two of the three. */
    memberId: number
    inventoryId: number
    /** A session that can press the owner's steps, where a body above the station can press them. */
    owner?: {page: Page; headers: Record<string, string>}
    close: () => Promise<void>
}

async function inventoryWithGear(page: Page, headers: Record<string, string>, label: string) {
    const stamp = `${label}-${Date.now()}-${Math.floor(performance.now())}`
    const made = await page.request.post('/api/v1/inventories',
        {headers, data: {name: `Aufstellung ${stamp}`, inventoryType: 'MIXED', hasSizes: false}})
    expect(made.ok(), `the station keeps an inventory (${await made.text()})`).toBeTruthy()
    return (await made.json()).id as number
}

async function piece(page: Page, headers: Record<string, string>, inventoryId: number, ownerKind: string) {
    const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
        headers,
        data: {internalId: `AUF-${Math.floor(Math.random() * 1e9)}`, name: 'Prüfstück',
            sizeId: null, metadata: null, ownerKind, ownerClusterId: null},
    })
    expect(made.ok(), `a piece is recorded (${await made.text()})`).toBeTruthy()
    return made.json()
}

async function ownMemberId(page: Page, headers: Record<string, string>) {
    const session = await page.request.get('/api/v1/session', {headers}).then(r => r.json())
    return session.member.id as number
}

/** The station everybody already knows, which answers to nobody. */
async function withoutCluster(page: Page): Promise<Setup> {
    const headers = await apiHeaders(page)
    return {
        label: 'a station with no association',
        page,
        headers,
        memberId: await ownMemberId(page, headers),
        inventoryId: await inventoryWithGear(page, headers, 'FREI'),
        close: async () => {},
    }
}

/**
 * A station under an association, which either keeps its gear here or does not.
 *
 * An association that keeps none has nobody to press its steps, so its stations walk the chain alone
 * and the record says the owner's steps were asserted. One that does keeps them for itself.
 */
async function underCluster(
    admin: Page,
    browser: Browser,
    request: APIRequestContext,
    managesGear: boolean,
): Promise<Setup> {
    const cluster = await ownCluster(admin, browser, request, managesGear ? 'GEFUEHRT' : 'UNGEFUEHRT')
    if (managesGear) {
        const said = await admin.request.put('/api/v1/cluster/inventory/settings',
            {headers: cluster.headers, data: {usesInventory: true}})
        expect(said.ok(), `the association says it keeps its gear here (${await said.text()})`).toBeTruthy()
    }

    const headers = await apiHeaders(cluster.stationPage)
    return {
        label: managesGear ? 'a station whose association keeps its gear here' : 'a station whose association does not',
        page: cluster.stationPage,
        headers,
        memberId: await ownMemberId(cluster.stationPage, headers),
        inventoryId: await inventoryWithGear(cluster.stationPage, headers, managesGear ? 'GEF' : 'UNG'),
        owner: managesGear ? {page: admin, headers: cluster.headers} : undefined,
        close: async () => {
            await cluster.stationPage.context().close()
        },
    }
}

async function detail(page: Page, headers: Record<string, string>, id: number) {
    return page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
}

/**
 * Walks a chain to its end, letting whichever session may press the step press it.
 *
 * The station presses its own steps and the association presses its own, which is the point of the
 * third configuration. Where nobody may press what is left, the walk stops and says which step.
 */
async function walkTogether(setup: Setup, id: number, arriving: () => Promise<Record<string, unknown>>) {
    for (let guard = 14; guard > 0; guard -= 1) {
        const seen = await detail(setup.page, setup.headers, id)
        if (seen.movement.state !== 'OPEN') return seen

        const current = seen.steps.find((step: {current: boolean}) => step.current)
        const hands = [
            {page: setup.page, headers: setup.headers},
            ...(setup.owner ? [setup.owner] : []),
        ]

        let pressed = false
        for (const hand of hands) {
            const theirs = await detail(hand.page, hand.headers, id)
            const step = theirs.steps.find((one: {current: boolean}) => one.current)
            if (!step?.actionable) continue
            const extra = step.picksItem ? await arriving() : {}
            const done = await hand.page.request.post(`/api/v1/movements/${id}/acknowledge`,
                {headers: hand.headers, data: {stepId: step.id, note: '', ...extra}})
            expect(done.ok(), `'${step.label}' was refused: ${await done.text()}`).toBeTruthy()
            pressed = true
            break
        }
        if (!pressed) {
            return {...seen, stoppedAt: current}
        }
    }
    return detail(setup.page, setup.headers, id)
}

/** Both exchanges, in one configuration. */
async function walkBothExchanges(setup: Setup) {
    for (const ownerKind of ['STATION', 'CLUSTER']) {
        const old = await piece(setup.page, setup.headers, setup.inventoryId, ownerKind)
        const spare = await piece(setup.page, setup.headers, setup.inventoryId, ownerKind)
        await setup.page.request.put(`/api/v1/inventory-items/${old.id}/assign`,
            {headers: setup.headers, data: {memberId: setup.memberId, memberName: null}})

        const started = await setup.page.request.post('/api/v1/movements', {
            headers: setup.headers,
            data: {purpose: 'EXCHANGE', memberId: setup.memberId, outgoingItemId: old.id,
                inventoryId: setup.inventoryId, reason: 'Zu klein'},
        })
        expect(started.ok(), `${setup.label}, ${ownerKind} gear: starting answered ${await started.text()}`)
            .toBeTruthy()
        const id = (await started.json()).movement.id

        const walked = await walkTogether(setup, id, async () => ({pickedItemId: spare.id}))

        // Every chain ends with the member confirming, and that is the member's own word: the walk
        // stops there and the station forces it, which is what a manager does for somebody absent.
        if (walked.movement.state === 'OPEN') {
            const waiting = walked.steps.find((step: {current: boolean}) => step.current)
            expect(waiting.actor, `${setup.label}, ${ownerKind} gear: stopped at '${waiting.label}'`).toBe('MEMBER')
            const forced = await setup.page.request.post(`/api/v1/movements/${id}/force`,
                {headers: setup.headers, data: {stepId: waiting.id, note: 'An der Wache übergeben'}})
            expect(forced.ok(), `${setup.label}: forcing answered ${await forced.text()}`).toBeTruthy()
        }

        const closed = await detail(setup.page, setup.headers, id)
        expect(closed.movement.state, `${setup.label}, ${ownerKind} gear: the chain finishes`).toBe('DONE')

        const replacement = await setup.page.request
            .get(`/api/v1/inventory-items/${spare.id}`, {headers: setup.headers})
            .then(r => r.json())
        expect(replacement.custody, `${setup.label}, ${ownerKind} gear: the member is wearing the replacement`)
            .toBe('WITH_MEMBER')
    }
}

test.describe('Movement chains in every station shape', () => {
    test('a station with no association walks both exchanges', async ({managerPage: page}) => {
        const setup = await withoutCluster(page)
        await walkBothExchanges(setup)
        await setup.close()
    })

    test('a station whose association keeps no gear here walks both exchanges',
        async ({adminPage, browser, request}) => {
            const setup = await underCluster(adminPage, browser, request, false)
            await walkBothExchanges(setup)
            await setup.close()
        })

    test('a station whose association keeps its gear here walks both exchanges',
        async ({adminPage, browser, request}) => {
            const setup = await underCluster(adminPage, browser, request, true)
            await walkBothExchanges(setup)
            await setup.close()
        })
})
