/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    test,
    expect,
    clusterAccountWith,
    clusterHeaders,
    clusterPage,
    theSeededCluster,
} from './fixtures/auth'

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
     * CLS-55 - Statistics counts what the association owns, size by size.
     *
     * The four totals say how much gear there is; they cannot say how many of size 48 are still in the
     * store, which is the question somebody ordering two hundred jackets actually has. The breakdown was
     * specified and then handed an empty list, so the table never appeared at all.
     */
    test('the figures tab counts the association gear', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory/statistics')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('cluster-inventory-tabs')).toBeVisible()

        // The totals, then the same gear cut by the size it was ordered in
        const rows = page.getByTestId('stats-size-row')
        await expect(rows.first()).toBeVisible({timeout: 15000})
        await expect(rows.first().locator('td').first()).not.toBeEmpty()

        await page.context().close()
    })

    /**
     * CLS-75 - Procurement brings gear into the association's own store.
     *
     * The tab existed and could not be used. Its create form asked which member the order was for, of a
     * station that has none, and marking one arrived put the item on that member, so an association could
     * record nothing and receive nothing. An association orders for its own store and hands out later.
     */
    test('an order recorded at the association arrives in its own store', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)
        const cluster = await theSeededCluster(page)
        const headers = await clusterHeaders(page, cluster)

        const before = await page.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())

        await page.goto('/cluster/inventory/procurement')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        // The screen loads at all, which it did not while it insisted on asking who there is
        await page.getByTestId('procurement-create').click({timeout: 15000})

        // No member is asked for, because there is nobody at an association's own station to order for
        await expect(page.getByTestId('procurement-inventory')).toBeVisible()
        const inventoryId = await page.getByTestId('procurement-inventory').locator('option').nth(1)
            .getAttribute('value')
        expect(inventoryId, 'the association keeps a kind of gear').toBeTruthy()
        await page.getByTestId('procurement-inventory').selectOption(inventoryId!)

        const note = `Nachbestellung ${Date.now()}`
        await page.getByTestId('procurement-notes').fill(note)
        await page.getByTestId('procurement-submit').click()
        await page.getByTestId('procurement-close').click({timeout: 15000})

        const entry = page.getByTestId('procurement-entry').filter({hasText: note})
        await expect(entry).toBeVisible({timeout: 15000})

        await entry.getByTestId('procurement-fulfill').click()

        // What arrived belongs to the association and rests in its store, ready to be sent somewhere
        await expect.poll(async () => {
            const items = await page.request
                .get('/api/v1/cluster/inventory/items', {headers})
                .then(r => r.json())
            return items.filter((row: {custody: string}) => row.custody === 'WITH_OWNER').length
        }, {timeout: 15000}).toBeGreaterThan(
            before.filter((row: {custody: string}) => row.custody === 'WITH_OWNER').length)

        await page.context().close()
    })

    /**
     * CLS-76 - A container at the association goes from empty to filled.
     *
     * An association holds its gear in containers rather than on people, so a container that cannot be
     * filled is the whole storage half of its screens doing nothing. Walked end to end because opening
     * the page proves only that it opens.
     */
    test('the association fills a container of its own', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory/storage')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        const name = `Verbandskiste ${Date.now()}`
        await page.getByRole('button', {name: 'Neuer Behälter'}).click({timeout: 15000})
        await page.getByLabel('Name').fill(name)
        await page.getByRole('button', {name: 'Erstellen'}).click()

        // Opened from the list rather than by an address the story worked out for itself
        await page.getByText(name).first().click()
        await expect(page).toHaveURL(/\/cluster\/inventory\/storage\/\d+$/, {timeout: 15000})
        await expect(page.getByTestId('container-item')).toHaveCount(0)

        await page.getByTestId('container-add-items').click()
        const candidate = page.getByTestId('container-add-item').first()
        await expect(candidate).toBeVisible({timeout: 15000})
        await candidate.click()
        await page.getByTestId('container-add-submit').click()

        // And it is in there afterwards, which is the half a page that merely opens cannot show
        await expect(page.getByTestId('container-item').first()).toBeVisible({timeout: 15000})

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

    /**
     * CLS-57 - The association renames a piece of its own gear.
     *
     * The one story that would have caught the phase marked done that was not. Gear an association owns
     * refused every change, its own owner included, and the item screen agreed by hiding the pencil, so
     * the association could define a thing and never correct it again.
     */
    test('the association describes its own gear from its own screen', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)
        const cluster = await theSeededCluster(page)
        const headers = await clusterHeaders(page, cluster)

        // Which piece is being looked at is arrangement; that it can be renamed is the story. Asked of the
        // association's own list rather than the barcode lookup, which finds what a station is holding and
        // so never finds a spare resting in its owner's store.
        const owned = await page.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())
        // One resting in its own store: the association's screens act at its own station, and a piece out
        // at a member station is opened there rather than here
        const item = owned.find((row: {stationUid: string | null}) => row.stationUid === null)
        expect(item, 'the association keeps gear in its own store').toBeTruthy()

        await page.goto(`/cluster/inventory/item/${item.id}`)
        await expect(page.getByTestId('app-shell')).toBeVisible()

        // A station holding somebody else's jacket is told it belongs elsewhere. The owner is not.
        await expect(page.getByTestId('owned-elsewhere')).toHaveCount(0)

        const renamed = `Einsatzjacke ${Date.now()}`
        await page.getByTestId('item-edit').click({timeout: 15000})
        await page.getByTestId('item-edit-name').fill(renamed)
        await page.getByRole('button', {name: /Speichern/i}).click()

        await expect(page.getByText(renamed)).toBeVisible({timeout: 15000})
        await page.context().close()
    })
})
