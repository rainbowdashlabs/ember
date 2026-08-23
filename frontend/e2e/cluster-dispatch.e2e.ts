/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, clusterAccountWith, clusterHeaders, clusterPage, theSeededCluster} from './fixtures/auth'

/**
 * Sending gear out of the association's store.
 *
 * <p>The association could define gear and never send it anywhere, which made the whole material area an
 * inventory of things that could not move. These stories walk the screen that moves them.
 */
test.describe('Dispatching the association gear', () => {
    // The consignments these stories send change what is in the store, which the next one reads
    test.describe.configure({mode: 'serial', timeout: 120_000})

    /**
     * CLS-58 - The association sends several pieces in one consignment.
     *
     * Its own screen rather than an action on each piece, because the everyday case is kitting a group out.
     * One movement carries the lot, so the station confirms one arrival rather than twenty.
     */
    test('the association sends a batch of gear to one of its stations', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)
        const cluster = await theSeededCluster(page)
        const headers = await clusterHeaders(page, cluster)

        const before = await page.request
            .get('/api/v1/cluster/inventory/dispatch', {headers})
            .then(r => r.json())
        expect(before.length, 'the association keeps gear in its store').toBeGreaterThan(0)

        await page.goto('/cluster/inventory/dispatch')
        await expect(page.getByTestId('dispatch-items')).toBeVisible({timeout: 15000})

        // Pick a station, then a piece, then send
        const options = page.getByTestId('dispatch-station-select').locator('option')
        const stationUid = await options.nth(1).getAttribute('value')
        expect(stationUid, 'the association has a station to send to').toBeTruthy()
        await page.getByTestId('dispatch-station-select').selectOption(stationUid!)

        const sent = before[0]
        await page.getByTestId(`dispatch-item-${sent.id}`).click()
        await page.getByTestId('dispatch-reason').fill('Für die neue Gruppe')
        await page.getByTestId('dispatch-send').click()

        // It is in the post: out of the store, and on a chain the station has yet to confirm
        await expect
            .poll(async () => {
                const after = await page.request
                    .get('/api/v1/cluster/inventory/dispatch', {headers})
                    .then(r => r.json())
                return after.some((item: {id: number}) => item.id === sent.id)
            }, {timeout: 15000})
            .toBeFalsy()

        await page.context().close()
    })

    /**
     * CLS-59 - A station is never offered a way to send gear out of a store it does not have.
     *
     * R4 applied to the one control that leads to the dispatch: the association's stock screen names it,
     * the station's own screen is the same screen and does not.
     */
    test('only the association is offered the way to send gear out', async ({managerPage, browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory')
        await expect(page.getByTestId('inventory-dispatch-link')).toBeVisible({timeout: 15000})

        await managerPage.goto('/station/inventory/manage')
        await expect(managerPage.getByTestId('app-shell')).toBeVisible({timeout: 15000})
        await expect(managerPage.getByTestId('inventory-dispatch-link')).toHaveCount(0)

        await page.context().close()
    })
})
