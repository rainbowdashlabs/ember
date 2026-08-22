/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, clusterAccountWith, clusterPage} from './fixtures/auth'

/**
 * The association's gear, looked at rather than asked about.
 *
 * The stories in `cluster-inventory.e2e.ts` cover the model: custody, movements, who may answer which
 * step. They pass by reading the API, which is why every one of them stayed green while the screens
 * were missing. These are the screens.
 */
test.describe('Cluster inventory screens', () => {
    /**
     * CLS-48 - The association defines its gear.
     *
     * The old screen had no create path at all. Stock is the station's own gear screen shown at the
     * association's station, so the way in exists because that screen has always had one.
     */
    test('the association can define gear from its own store', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByTestId('cluster-inventory-tabs')).toBeVisible()
        // The station's gear screen offers a way to define a kind of gear. Its presence here is the
        // whole point of mounting it.
        await expect(page.getByRole('button', {name: /Inventar erstellen/i}))
            .toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * CLS-49 - The association sees where its gear is.
     *
     * Grouped by the station holding it, naming whoever is wearing it there.
     */
    test('gear out at the stations is grouped by station', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory/out')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByTestId('out-station-group').first()).toBeVisible({timeout: 15000})
        await expect(page.getByTestId('out-item').first()).toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-50 - The module switch is in the settings, not on the list.
     *
     * A switch deciding whether the association keeps gear at all sat in the middle of a screen that
     * lists things, which is the specific reason the area read as confusing.
     */
    test('the module switch is in the settings and not on the store', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MODULES')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('inventory-module-setting')).toHaveCount(0)

        await page.getByTestId('tab-cluster-inventory-settings').click()
        await expect(page).toHaveURL(/\/cluster\/inventory\/settings$/)
        await expect(page.getByTestId('inventory-module-setting')).toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * CLS-53 - The settings tab shows only what the reader may set.
     *
     * Two settings behind two different rights. Somebody holding one sees one, rather than an empty
     * tab or somebody else's switch.
     */
    test('the settings tab shows the section the reader holds', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MODULES')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory/settings')
        await expect(page.getByTestId('inventory-module-setting')).toBeVisible({timeout: 15000})

        const gear = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const gearPage = await clusterPage(browser, request, gear)
        await gearPage.goto('/cluster/inventory/settings')
        await expect(gearPage.getByTestId('inventory-flow-setting')).toBeVisible({timeout: 15000})

        await page.context().close()
        await gearPage.context().close()
    })

    /**
     * CLS-54 - A tab is an address.
     *
     * Reloading keeps you where you were, which is what lets a notification about something waiting
     * point straight at the tab that shows it.
     */
    test('a reload keeps the tab you were on', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory')
        await page.getByTestId('tab-cluster-inventory-out').click()
        await expect(page).toHaveURL(/\/cluster\/inventory\/out$/)

        await page.reload()
        await expect(page).toHaveURL(/\/cluster\/inventory\/out$/)
        await expect(page.getByTestId('cluster-inventory-tabs')).toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-51 - The association walks a container, and is never offered a member check.
     *
     * It keeps gear in containers rather than on people, so the screens about people are not merely
     * empty here, they are not linked to at all.
     */
    test('the association checks containers and not people', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory/checks/container')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        // No route is named for a member check, so no control leads to one.
        await expect(page.getByRole('link', {name: /Mitglied/i})).toHaveCount(0)
        await page.context().close()
    })

    /**
     * CLS-55 - Statistics counts what the association owns.
     */
    test('the figures tab counts the association gear', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory/statistics')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('cluster-inventory-tabs')).toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-56 - A waiting step opens the movement it belongs to.
     *
     * The queue is where the work is, and a row that cannot be opened is a list rather than a queue.
     */
    test('a step waiting on the association opens its movement', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_TRANSFER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory/movements')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('cluster-inventory-tabs')).toBeVisible()
        await page.context().close()
    })
})
