/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {ownCluster, type OwnCluster} from './fixtures/cluster'

const MIB = 1024 * 1024
const GIB = 1024 * MIB

/**
 * The room an association hands out: its tiers, its defaults, what each station was granted, and what the
 * instance may still say about a station that answers to somebody else.
 *
 * Every story builds an association of its own with a station under it. Room reaches every member station at
 * once and the pool is one number for all of them, so handing some out on the seeded association would move
 * figures four other workers are reading at that moment.
 */
test.describe('Cluster storage', () => {
    /**
     * The association's storage screen, entered the way the switcher enters it.
     *
     * The key is planted rather than clicked because none of these stories is about the switcher, and the
     * screen has to be loaded twice for the same reason the governance stories load it twice: the first load
     * is what gives the page an origin to plant the key on.
     */
    async function storageScreen(page: Page, own: OwnCluster) {
        await page.goto('/cluster/storage')
        await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
        await page.goto('/cluster/storage')
        await expect(page.getByTestId('app-shell')).toBeVisible()
    }

    /** What the instance grants the association to hand out, which is the instance's act rather than its own. */
    async function grantPool(page: Page, own: OwnCluster, bytes: number) {
        const headers = await apiHeaders(page)
        const pooled = await page.request.put(`/api/v1/clusters/${own.uid}/storage-pool`,
            {headers, data: {quotaBytes: bytes}})
        expect(pooled.ok()).toBeTruthy()
    }

    /** One row of the station table, told from the association's own store by the station's name. */
    function stationRow(page: Page, name: string) {
        return page.getByTestId('storage-station-row').filter({hasText: name})
    }

    /**
     * CLS-77 - The association keeps its own tiers.
     *
     * A tier is made on the association's screen and handed to two stations at once, and both rows then name
     * it and carry its numbers. The instance's tiers are a different set entirely: an association has to be
     * able to express a step the instance never thought of.
     */
    test('the association keeps its own tiers', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Stufenverband')
        await grantPool(page, own, 20 * GIB)

        // Named without the word the fixture's station carries, so a row can be told from the other by name
        const second = await page.request.post('/api/v1/cluster/stations',
            {headers: own.headers, data: {name: `Löschzug ${own.name}`}})
        expect(second.ok()).toBeTruthy()
        const secondName = (await second.json()).name

        await storageScreen(page, own)

        await page.getByTestId('tier-create').click()
        await page.getByTestId('tier-name').fill('Mittelstufe')
        await page.getByTestId('quota-field-total').fill('2')
        await page.getByTestId('tier-save').click()

        const tier = page.getByTestId('tier-row').filter({hasText: 'Mittelstufe'})
        await expect(tier).toBeVisible({timeout: 15000})
        await expect(tier).toContainText('2.0 GiB')

        // Handed to two stations in one act, which is what the association does instead of typing the same
        // seven numbers at every station it runs
        await tier.getByRole('button', {name: 'Anwenden'}).click()
        const applying = page.getByTestId('modal')
        await applying.locator('label').filter({hasText: own.stationName}).getByRole('switch').click()
        await applying.locator('label').filter({hasText: secondName}).getByRole('switch').click()
        await page.getByTestId('tier-apply-save').click()

        for (const name of [own.stationName, secondName]) {
            await expect(stationRow(page, name)).toContainText('Mittelstufe', {timeout: 15000})
            await expect(stationRow(page, name)).toContainText('2.0 GiB')
        }
    })

    /**
     * CLS-78 - The pool refuses a promise the association cannot keep.
     *
     * On the screen rather than through the endpoint, because being told why is the whole of it: the refusal
     * has to name the pool and what is already out of it, or the person handing room out has no way to know
     * what would fit.
     */
    test('the pool refuses a promise the association cannot keep', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Knappverband')
        await grantPool(page, own, 1 * GIB)

        await storageScreen(page, own)
        await expect(page.getByTestId('cluster-pool-usage')).toContainText('1.0 GiB', {timeout: 15000})

        const row = stationRow(page, own.stationName)
        await row.getByTestId('station-room-edit').click()
        await page.getByTestId('quota-field-total').fill('5')
        await page.getByTestId('station-room-save').click()

        // Named in bytes, which is what the association promised in, and it says how much is already gone
        const refusal = page.getByText(/its pool is/i)
        await expect(refusal).toBeVisible({timeout: 15000})
        await expect(refusal).toContainText(String(1 * GIB))

        // Nothing was handed out, so the pool figure has not moved
        await expect(page.getByTestId('cluster-pool-usage')).toContainText('0 B')
    })

    /**
     * CLS-79 - A station on the association's defaults says so.
     *
     * The number a station lives on is worth nothing without whose word it is on: the same 1 GiB means "this
     * is what we give everybody" until somebody grants it, and after that it means "this is what we gave you".
     */
    test('a station on the association\'s defaults says so', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Standardverband')
        await grantPool(page, own, 20 * GIB)

        await storageScreen(page, own)

        await page.getByTestId('cluster-defaults-edit').click()
        await page.getByTestId('quota-field-total').fill('1')
        await page.getByTestId('cluster-defaults-save').click()

        await expect(page.getByTestId('cluster-defaults')).toContainText('1.0 GiB', {timeout: 15000})
        const row = stationRow(page, own.stationName)
        await expect(row).toContainText('1.0 GiB')
        await expect(row).toContainText('Standard des Verbands')

        // Granted to that one station, which is a different sentence about the same station
        await row.getByTestId('station-room-edit').click()
        await page.getByTestId('quota-field-total').fill('3')
        await page.getByTestId('station-room-save').click()

        await expect(row).toContainText('3.0 GiB', {timeout: 15000})
        await expect(row).toContainText('Vom Verband vergeben')
    })

    /**
     * CLS-80 - The instance stops overriding a station under an association.
     *
     * The instance's lever on an association is the pool it grants it, and inside that pool the association
     * decides. A number set here would change nothing anybody can see, so the screen says who decides instead
     * of offering an edit that does nothing.
     */
    test('the instance does not override a station under an association', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Instanzverband')
        await grantPool(page, own, 20 * GIB)

        await page.goto('/admin/monitoring/storage')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        // Nothing handed out yet, so the station still lives on what the instance says and the instance may
        // still put it back there
        const row = stationRow(page, own.stationName)
        await expect(row.getByTestId('station-room-reset')).toBeEnabled({timeout: 15000})

        const granted = await page.request.put(`/api/v1/cluster/storage/stations/${own.stationUid}`,
            {headers: own.headers, data: {totalBytes: 3 * GIB}})
        expect(granted.ok()).toBeTruthy()

        await page.reload()
        await expect(row).toContainText('Vom Verband vergeben', {timeout: 15000})
        await expect(row).toContainText('3.0 GiB')
        await expect(row.getByTestId('station-room-reset')).toBeDisabled()
    })

    /**
     * CLS-81 - A released station keeps nothing.
     *
     * The room went with the membership. What the instance says about the station stands again, and the
     * association has the room back to hand to somebody else.
     */
    test('a released station keeps nothing', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Abschiedsverband')
        await grantPool(page, own, 20 * GIB)

        const granted = await page.request.put(`/api/v1/cluster/storage/stations/${own.stationUid}`,
            {headers: own.headers, data: {totalBytes: 2 * GIB}})
        expect(granted.ok()).toBeTruthy()

        await storageScreen(page, own)
        await expect(page.getByTestId('cluster-pool-usage')).toContainText('2.0 GiB von 20.0 GiB', {timeout: 15000})

        const released = await page.request.delete(`/api/v1/cluster/stations/${own.stationUid}`,
            {headers: own.headers})
        expect(released.ok()).toBeTruthy()

        await page.reload()
        await expect(page.getByTestId('cluster-pool-usage')).toContainText('0 B von 20.0 GiB', {timeout: 15000})
        await expect(stationRow(page, own.stationName)).toHaveCount(0)

        // And at the instance the station reads as nobody else's business again
        await page.goto('/admin/monitoring/storage')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        const row = stationRow(page, own.stationName)
        await expect(row).toContainText('Standard', {timeout: 15000})
        await expect(row).not.toContainText('Vom Verband vergeben')
        await expect(row.getByTestId('station-room-reset')).toBeEnabled()
    })
})
