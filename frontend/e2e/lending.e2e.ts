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

    /** An inventory of two pieces on the acting station, made for one story and named after it. */
    async function stockedInventory(page: Page, name: string): Promise<number> {
        const headers = await apiHeaders(page)
        const created = await page.request.post('/api/v1/inventories', {
            headers,
            data: {name, inventoryType: 'INTERNAL', hasSizes: false},
        })
        if (!created.ok()) throw new Error(`Creating the inventory answered ${created.status()}`)
        const inventory = await created.json()
        for (const label of ['A', 'B']) {
            await page.request.post(`/api/v1/inventories/${inventory.id}/items`, {
                headers,
                data: {internalId: `${name}-${label}`, name: `${name} ${label}`},
            })
        }
        return inventory.id
    }

    /**
     * The whole of the opt-in: nothing reaches the partner until the owner says so, and holding the
     * gear back takes it off the partner's screen again.
     */
    test('a partner finds gear only once the owning station offers it', async ({managerPage, browser, request}) => {
        const ownerHeaders = await apiHeaders(managerPage)
        const ownerStationId = ownerHeaders['X-Station-Id']
        const borrower = await partnerOf(request, ownerStationId)
        const borrowerPage = await pageAsThrowaway(browser, request, [], borrower)
        const borrowerHeaders = await apiHeaders(borrowerPage)

        const name = unique('Ausleihregal')
        const inventoryId = await stockedInventory(managerPage, name)

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
            expect(offered?.find(entry => entry.inventoryName === name)?.availableCount).toBe(2)
        }).toPass()

        await managerPage.goto('/station/inventory/lending/shares')
        await expect(managerPage.getByTestId('app-shell')).toBeVisible()
        await expect(managerPage.getByTestId('lending-shares-offered').getByText(name)).toBeVisible()

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
