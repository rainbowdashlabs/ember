/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * Where an exchange says it stands, and what may move it.
 *
 * An exchange never announces its own status: it is read off where the two pieces are. That makes it
 * easy for something that touched neither of them to look like a step forward, which is what these
 * stories hold the line on. Nothing but walking the chain moves an exchange, and an exchange that was
 * called off says so rather than passing for a finished one.
 */

/** A piece of the station's own gear in an inventory of this story's own, already on a member. */
async function exchangeOnItsWay(page: Page, headers: Record<string, string>, label: string) {
    const stamp = `${label}-${Date.now()}-${Math.floor(performance.now())}`
    const inventoryName = `Tauschinventar ${stamp}`
    const inventory = await page.request.post('/api/v1/inventories',
        {headers, data: {name: inventoryName, inventoryType: 'MIXED', hasSizes: false}})
    expect(inventory.ok(), 'the station keeps an inventory for this story').toBeTruthy()
    const inventoryId = (await inventory.json()).id

    const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
        headers,
        data: {internalId: stamp, name: 'Prüfstück', sizeId: null, metadata: null,
            ownerKind: 'STATION', ownerClusterId: null},
    })
    expect(made.ok(), 'the station records a piece to exchange').toBeTruthy()
    const item = await made.json()

    const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
    const member = (Array.isArray(members) ? members : members.members ?? [])
        .find((m: {userType: string}) => m.userType === 'MEMBER')
    expect(member, 'the station has an ordinary member').toBeTruthy()

    await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
        {headers, data: {memberId: member.id, memberName: null}})

    const raised = await page.request.post('/api/v1/exchanges', {
        headers,
        data: {memberId: member.id, itemId: item.id, inventoryId, oldSizeId: null, newSizeId: null,
            reason: 'Passt nicht mehr'},
    })
    expect(raised.ok(), 'the exchange is raised').toBeTruthy()
    return {id: (await raised.json()).id, inventoryName}
}

/** What the station's list says about one exchange, asked of the API rather than the screen. */
async function statusOf(page: Page, headers: Record<string, string>, id: number) {
    const rows = await page.request.get('/api/v1/exchanges', {headers}).then(r => r.json())
    return rows.find((row: {id: number}) => row.id === id)?.status
}

test.describe('Exchange status', () => {
    /**
     * EXS-1 - Opening an exchange and closing it again leaves it exactly where it stood.
     *
     * The panel that advances an exchange is a form, and its second button closes the form. It has been
     * mistaken for one that does something to the exchange, so the story watches the wire as well as the
     * screen: not one request that could change anything may leave while the panel is opened and closed.
     */
    test('opening an exchange and closing it again changes nothing', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {id, inventoryName} = await exchangeOnItsWay(page, headers, 'OPEN')

        const before = await statusOf(page, headers, id)
        expect(before, 'a fresh exchange is announced').toBe('ANNOUNCED')
        const logsBefore = await page.request
            .get(`/api/v1/exchanges/${id}/logs`, {headers})
            .then(r => r.json())

        await page.goto('/station/inventory/exchanges')
        const row = page.getByTestId('exchange-row').filter({hasText: inventoryName})
        await expect(row).toBeVisible({timeout: 15000})
        await expect(row).toContainText('Angekündigt')

        const changing: string[] = []
        page.on('request', request => {
            if (request.method() === 'GET') return
            if (/\/api\/v1\/(exchanges|movements)/.test(request.url())) changing.push(`${request.method()} ${request.url()}`)
        })

        await row.getByTestId('exchange-advance').click()
        await expect(page.getByTestId('exchange-status')).toBeVisible()
        await page.getByTestId('exchange-status-cancel').click()
        await expect(page.getByTestId('exchange-status')).toBeHidden()

        expect(changing, 'closing the panel asks the server for nothing').toEqual([])
        await expect(row).toContainText('Angekündigt')

        expect(await statusOf(page, headers, id), 'and the exchange stands where it stood').toBe(before)
        const logsAfter = await page.request
            .get(`/api/v1/exchanges/${id}/logs`, {headers})
            .then(r => r.json())
        expect(logsAfter.length, 'with nothing added to its history').toBe(logsBefore.length)
    })

    /**
     * EXS-2 - An exchange that was called off does not pass for a finished one.
     *
     * Calling one off stops it; it does not complete it. Reading every stopped exchange as finished made
     * pressing the button that calls one off look exactly like pressing the one that completes it, and
     * the row moved from the beginning of its chain to the end of it.
     */
    test('an exchange called off reads as called off, not as finished', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {id, inventoryName} = await exchangeOnItsWay(page, headers, 'CALLOFF')

        const calledOff = await page.request.post(`/api/v1/movements/${id}/cancel`,
            {headers, data: {reason: 'Passt doch'}})
        expect(calledOff.ok(), 'the station calls the exchange off').toBeTruthy()

        expect(await statusOf(page, headers, id), 'which is not the same as finishing it').toBe('CANCELLED')

        await page.goto('/station/inventory/exchanges')
        const row = page.getByTestId('exchange-row').filter({hasText: inventoryName})
        await expect(row).toBeVisible({timeout: 15000})
        await expect(row.getByTestId('exchange-status-closed')).toHaveText('Abgebrochen')
        await expect(row.getByTestId('exchange-advance'),
            'and there is nothing left to advance it to').toBeHidden()
    })

    /**
     * EXS-3 - An exchange standing further along than it should is set back by hand.
     *
     * There is no status to overwrite: it is read off where the two pieces are, so setting one means
     * putting the pieces where that status reads them. The reason is not optional, and the history says
     * afterwards that a person set this rather than that anybody walked it.
     */
    test('an exchange that stands too far is set back, with the reason on record',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const {id, inventoryName} = await exchangeOnItsWay(page, headers, 'BACK')

            const walked = await page.request.put(`/api/v1/exchanges/${id}/status`,
                {headers, data: {status: 'RECEIVED'}})
            expect(walked.ok(), await walked.text()).toBeTruthy()
            expect(await statusOf(page, headers, id)).toBe('RECEIVED')

            await page.goto('/station/inventory/exchanges')
            const row = page.getByTestId('exchange-row').filter({hasText: inventoryName})
            await expect(row).toBeVisible({timeout: 15000})

            await row.getByTestId('exchange-correct').click()
            await expect(page.getByTestId('exchange-correct-panel')).toBeVisible()
            await page.getByTestId('exchange-correct-status').selectOption('ANNOUNCED')
            await page.getByTestId('exchange-correct-reason').fill('Wurde nie abgegeben')
            await page.getByTestId('exchange-correct-submit').click()

            await expect(row).toContainText('Angekündigt')
            expect(await statusOf(page, headers, id), 'and it stands there when asked again').toBe('ANNOUNCED')

            const history = await page.request
                .get(`/api/v1/exchanges/${id}/logs`, {headers})
                .then(r => r.json())
            expect(
                history.some((entry: {ackKind: string, note: string}) =>
                    entry.ackKind === 'CORRECTED' && entry.note === 'Wurde nie abgegeben'),
                'the history says a person set this, and why',
            ).toBeTruthy()
        })
})
