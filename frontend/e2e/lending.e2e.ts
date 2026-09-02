/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test, apiHeaders, pageAsThrowaway} from './fixtures/auth'
import type {DemoAccount} from './fixtures/auth'
import {unique} from './fixtures/unique'
import type {APIRequestContext, Page} from '@playwright/test'

interface OfferedEntry {
    inventoryId: number
    inventoryName: string
    stationName: string
    availableCount: number
}

interface StationGroup {
    stationId: string
    stationName: string
    accounts: { email: string }[]
}

/**
 * What one station offers another, seen from both ends at once.
 *
 * <p>Sharing is opt-in, and an opt-in can only be shown by two stations together: one says what it
 * offers and the other looks and finds it, or does not. Driven from one side it proves nothing,
 * because the side that decides is not the side that looks.
 */
test.describe('Lending offer', () => {

    /** What the browse endpoint answers, as whoever holds these headers. */
    async function browse(
        request: APIRequestContext,
        headers: Record<string, string>,
    ): Promise<OfferedEntry[] | null> {
        const response = await request.get('/api/v1/federated/lending/available', {headers})
        if (!response.ok()) return null
        return (await response.json()).entries
    }

    /**
     * A station that is really a lending partner of the given one, found by asking rather than
     * assumed: "some other station" is not the same as "a station this one is federated with", and
     * the seeder is free to arrange the partnerships however it likes.
     */
    async function partnerOf(request: APIRequestContext, stationId: string): Promise<DemoAccount> {
        const listing = await request.get('/api/v1/demo/accounts')
        const groups: StationGroup[] = (await listing.json()).stationGroups
        const ownerName = groups.find(group => group.stationId === stationId)?.stationName
        if (!ownerName) throw new Error('The acting station is not among the demo stations')

        for (const group of groups) {
            if (group.stationId === stationId) continue
            for (const candidate of group.accounts) {
                if (!candidate.email) continue
                const login = await request.post('/api/v1/demo/login', {data: {email: candidate.email}})
                if (!login.ok()) continue
                const {token} = await login.json()
                const entries = await browse(request, {
                    Authorization: `Bearer ${token}`,
                    'X-Station-Id': group.stationId,
                })
                if (entries?.some(entry => entry.stationName === ownerName)) {
                    return {...candidate, stationId: group.stationId} as DemoAccount
                }
            }
        }
        throw new Error('No station borrows from this one, so there is no offer to show')
    }

    /**
     * A drawer of two pieces on the acting station, one of them of a kind of its own, made for one
     * story and named after it. It is a drawer of different things rather than a shelf of one thing,
     * because that is the only kind of inventory that may have kinds at all.
     */
    /**
     * How many pieces of one inventory a partner is offered, over every row it comes back in. The
     * answer is a row per kind of thing rather than one per drawer, so a drawer holding a kind and a
     * piece without one answers in two rows that mean one number.
     */
    function offeredCount(offered: {inventoryName: string; availableCount: number}[] | undefined, name: string): number {
        return (offered ?? [])
            .filter(entry => entry.inventoryName === name)
            .reduce((sum, entry) => sum + entry.availableCount, 0)
    }

    async function stockedInventory(page: Page, name: string): Promise<{ inventoryId: number; artId: number }> {
        const headers = await apiHeaders(page)
        const created = await page.request.post('/api/v1/inventories', {
            headers,
            data: {name, inventoryType: 'INTERNAL', hasSizes: false, homogeneous: false},
        })
        if (!created.ok()) throw new Error(`Creating the inventory answered ${created.status()}`)
        const inventory = await created.json()

        const madeArt = await page.request.post(`/api/v1/inventories/${inventory.id}/arts`, {
            headers,
            data: {name: `${name} gut`, note: ''},
        })
        if (!madeArt.ok()) throw new Error(`Creating the kind answered ${madeArt.status()}`)
        const art = await madeArt.json()

        for (const label of ['A', 'B']) {
            const item = await page.request.post(`/api/v1/inventories/${inventory.id}/items`, {
                headers,
                data: {internalId: `${name}-${label}`, name: `${name} ${label}`},
            })
            if (label === 'A') {
                const body = await item.json()
                await page.request.put(`/api/v1/inventories/${inventory.id}/item-arts`, {
                    headers,
                    data: {artId: art.id, itemIds: [body.id]},
                })
            }
        }
        return {inventoryId: inventory.id, artId: art.id}
    }

    /**
     * The whole of the opt-in, and the narrowest row deciding. Nothing reaches the partner until the
     * owner offers the inventory; holding one kind back out of it takes that kind and no more; and
     * holding the inventory back takes the rest.
     */
    test('a partner finds gear only once the owning station offers it', async ({managerPage, browser, request}) => {
        const ownerHeaders = await apiHeaders(managerPage)
        const ownerStationId = ownerHeaders['X-Station-Id']
        const borrower = await partnerOf(request, ownerStationId)
        const borrowerPage = await pageAsThrowaway(browser, request, [], borrower)
        const borrowerHeaders = await apiHeaders(borrowerPage)

        const name = unique('Ausleihregal')
        const {inventoryId} = await stockedInventory(managerPage, name)

        const before = await browse(request, borrowerHeaders)
        expect(before?.some(entry => entry.inventoryName === name)).toBe(false)

        await managerPage.goto(`/station/inventory/detail/${inventoryId}`)
        await expect(managerPage.getByTestId('app-shell')).toBeVisible()
        await expect(managerPage.getByTestId('lending-share-state')).toHaveText('Nicht angeboten')

        await managerPage.getByTestId('lending-share-edit').click()
        await expect(managerPage.getByTestId('lending-share-modal')).toBeVisible()
        await managerPage.getByTestId('lending-share-grant').selectOption('GRANT')
        await managerPage.getByTestId('lending-share-scope').selectOption('ALL_PARTNERS')
        await managerPage.getByTestId('lending-share-save').click()
        await expect(managerPage.getByTestId('lending-share-state')).toHaveText('Allen Partnerwachen angeboten')

        await expect(async () => {
            const offered = await browse(request, borrowerHeaders)
            expect(offeredCount(offered, name), 'the drawer is offered whole').toBe(2)
        }).toPass()

        await managerPage.goto('/station/inventory/lending/shares')
        await expect(managerPage.getByTestId('app-shell')).toBeVisible()
        await expect(managerPage.getByTestId('lending-shares-offered').getByText(name)).toBeVisible()

        await managerPage.goto(`/station/inventory/edit/${inventoryId}`)
        await expect(managerPage.getByTestId('inventory-arts')).toBeVisible()
        await managerPage.getByTestId('lending-share-button').first().click()
        await expect(managerPage.getByTestId('lending-share-modal')).toBeVisible()
        await managerPage.getByTestId('lending-share-grant').selectOption('WITHHOLD')
        await managerPage.getByTestId('lending-share-save').click()

        await expect(async () => {
            const offered = await browse(request, borrowerHeaders)
            expect(offeredCount(offered, name), 'the withheld kind is gone and the rest stays').toBe(1)
        }).toPass()

        await managerPage.goto(`/station/inventory/detail/${inventoryId}`)
        await managerPage.getByTestId('lending-share-edit').click()
        await managerPage.getByTestId('lending-share-grant').selectOption('WITHHOLD')
        await managerPage.getByTestId('lending-share-save').click()
        await expect(managerPage.getByTestId('lending-share-state')).toHaveText('Zurückgehalten')

        await expect(async () => {
            const offered = await browse(request, borrowerHeaders)
            expect(offered?.some(entry => entry.inventoryName === name)).toBe(false)
        }).toPass()

        await borrowerPage.context().close()
    })

    /**
     * The switch where the inventories are, which is where somebody looking for it looks first.
     *
     * <p>The list carries the decision as a label and the control beside it, so turning an offer on
     * and off again never means opening the inventory, and what every drawer is offered as is read
     * off one screen.
     */
    test('the offer is switched on and off again from the list of inventories', async ({managerPage: page}) => {
        const name = unique('Listenregal')
        await stockedInventory(page, name)

        await page.goto('/station/inventory/manage')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        const card = page.getByTestId('inventory-card').filter({has: page.getByText(name, {exact: true})})
        await expect(card.getByTestId('inventory-badge-share')).toHaveText('Nicht angeboten')

        await card.getByTestId('lending-share-button').click()
        await expect(page.getByTestId('lending-share-modal')).toBeVisible()
        await page.getByTestId('lending-share-grant').selectOption('GRANT')
        await page.getByTestId('lending-share-scope').selectOption('ALL_PARTNERS')
        await page.getByTestId('lending-share-save').click()
        await expect(card.getByTestId('inventory-badge-share')).toHaveText('Allen Partnerwachen angeboten')

        await card.getByTestId('lending-share-button').click()
        await expect(page.getByTestId('lending-share-modal')).toBeVisible()
        await page.getByTestId('lending-share-clear').click()
        await expect(card.getByTestId('inventory-badge-share')).toHaveText('Nicht angeboten')
    })

    /**
     * An inventory of the body above the station is not the station's to lend, so the list offers no
     * decision about it at all rather than one that would be refused on save.
     */
    test('an inventory of the body above the station offers no decision', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const name = unique('Kreisregal')
        const created = await page.request.post('/api/v1/inventories', {
            headers,
            data: {name, inventoryType: 'EXTERNAL', hasSizes: false, homogeneous: false},
        })
        if (!created.ok()) throw new Error(`Creating the inventory answered ${created.status()}`)
        const inventory = await created.json()

        await page.goto('/station/inventory/manage')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        const card = page.getByTestId('inventory-card').filter({has: page.getByText(name, {exact: true})})
        await expect(card).toBeVisible()
        await expect(card.getByTestId('inventory-badge-share')).toHaveCount(0)
        await expect(card.getByTestId('lending-share-button')).toHaveCount(0)

        await page.goto(`/station/inventory/detail/${inventory.id}`)
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('lending-share-panel')).toHaveCount(0)

        const refused = await page.request.put(`/api/v1/lending/shares/inventory/${inventory.id}`, {
            headers,
            data: {grant: 'GRANT', scope: 'ALL_PARTNERS', partnerIds: []},
        })
        expect(refused.status(), 'and the backend refuses it rather than writing it down').toBe(400)
    })

    /**
     * The screen the owning station reads its own offer off, which is the only place the two
     * decisions stand side by side.
     */
    test('the offer screen separates what is given from what is held back', async ({managerPage: page}) => {
        await page.goto('/station/inventory/lending/shares')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('lending-shares-offered')).toBeVisible()
        await expect(page.getByTestId('lending-shares-withheld')).toBeVisible()
    })

    /** The way into the offer screen, which only somebody who may manage lending is shown. */
    test('the lending screen leads to what the station offers', async ({managerPage: page}) => {
        await page.goto('/station/inventory/lending')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await page.getByTestId('lending-shares-link').click()
        await page.waitForURL(/\/station\/inventory\/lending\/shares/)
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
