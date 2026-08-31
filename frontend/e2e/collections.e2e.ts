/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, clusterAccountWith, clusterPage} from './fixtures/auth'
import {unique} from './fixtures/unique'

test.describe('Collections', () => {
    /**
     * The whole point of the screen in one walk: name a set, put a counted line in it, and read back
     * how much of it the station could actually fetch.
     */
    test('a collection is created, filled and read against the stock', async ({managerPage: page}) => {
        const name = unique('Sammlung')

        await page.goto('/station/inventory/collections')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByTestId('collection-create').click()
        await page.getByTestId('collection-name').fill(name)
        await page.getByTestId('collection-save').click()

        await expect(page.getByTestId('collection-entry').filter({hasText: name})).toBeVisible()

        await page.getByTestId('collection-add-line').click()
        await page.getByTestId('collection-line-kind').selectOption('inventory')
        await page.getByTestId('line-target-inventory').selectOption({index: 1})
        await page.getByTestId('line-target-quantity').fill('2')
        await page.getByTestId('collection-line-submit').click()

        await expect(page.getByTestId('collection-line')).toHaveCount(1)
        await expect(
            page.getByTestId('collection-line-filled').or(page.getByTestId('collection-line-short')),
        ).toBeVisible()

        await page.reload()
        await page.getByTestId('collection-entry').filter({hasText: name}).click()
        await expect(page.getByTestId('collection-line')).toHaveCount(1)
    })

    /**
     * The line the whole idea turns on: four of one kind, not four of whatever the drawer holds. The
     * seeded radio drawer has six blue radios and a case nobody gave a kind to, so a line asking for
     * four blue ones fills and would not have if it had counted the drawer.
     */
    test('a line asks for a count of one kind', async ({managerPage: page}) => {
        const name = unique('Artzeile')

        await page.goto('/station/inventory/collections')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByTestId('collection-create').click()
        await page.getByTestId('collection-name').fill(name)
        await page.getByTestId('collection-save').click()

        await page.getByTestId('collection-add-line').click()
        await page.getByTestId('collection-line-kind').selectOption('art')
        await page.getByTestId('line-target-art').selectOption({label: 'Funkgerät blau (Handfunkgeräte)'})
        await page.getByTestId('line-target-art-quantity').fill('4')
        await page.getByTestId('collection-line-submit').click()

        await expect(page.getByTestId('collection-line')).toHaveCount(1)
        await expect(page.getByTestId('collection-line-filled')).toHaveText('4 von 4')

        await page.getByTestId('collection-line-count').fill('9')
        await expect(page.getByTestId('collection-line-short')).toHaveText('6 von 9')
    })

    /** An association's gear is a station's gear, so its collections are the home station's, shown here. */
    test('the association reads the collections of its home station', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/inventory/collections')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByTestId('tab-cluster-inventory-collections')).toBeVisible({timeout: 15000})
        await expect(page.getByTestId('collections-panel')).toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * The line goes with the piece it names, so the question before the deletion has to say which
     * collections stand to lose one. A dedicated drawer and piece keep the story from depending on
     * whatever the demo data happens to hold.
     */
    test('deleting a piece names the collections that lose a line', async ({managerPage: page}) => {
        const collectionName = unique('Warnung')
        const itemName = unique('Stueck')

        await page.goto('/station/inventory/collections')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        const headers = await apiHeaders(page)

        const drawer = await (await page.request.post('/api/v1/inventories', {
            headers,
            data: {name: unique('Schrank'), inventoryType: 'INTERNAL', hasSizes: false},
        })).json()
        const piece = await (await page.request.post(`/api/v1/inventories/${drawer.id}/items`, {
            headers,
            data: {name: itemName, internalId: null},
        })).json()
        const collection = await (await page.request.post('/api/v1/inventory-collections', {
            headers,
            data: {name: collectionName, note: ''},
        })).json()
        await page.request.post(`/api/v1/inventory-collections/${collection.id}/lines`, {
            headers,
            data: {itemId: piece.id, quantity: 1},
        })

        await page.goto(`/station/inventory/detail/${drawer.id}`)
        const row = page.getByRole('row').filter({hasText: itemName})
        await expect(row).toBeVisible()
        await row.getByRole('button', {name: 'Aktionen'}).click()
        await page.getByRole('button', {name: 'Löschen'}).first().click()

        await expect(page.getByTestId('modal')).toContainText(collectionName)
    })

    /** A piece can go into a kit from its own page, so a kit fills from either side. */
    test('a piece is put into a collection from its own page', async ({managerPage: page}) => {
        const collectionName = unique('VonDerSeite')

        await page.goto('/station/inventory/collections')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        const headers = await apiHeaders(page)

        const drawer = await (await page.request.post('/api/v1/inventories', {
            headers,
            data: {name: unique('Fach'), inventoryType: 'INTERNAL', hasSizes: false},
        })).json()
        const piece = await (await page.request.post(`/api/v1/inventories/${drawer.id}/items`, {
            headers,
            data: {name: unique('Beamer'), internalId: null},
        })).json()
        const collection = await (await page.request.post('/api/v1/inventory-collections', {
            headers,
            data: {name: collectionName, note: ''},
        })).json()

        await page.goto(`/station/inventory/item/${piece.id}`)
        await page.getByTestId('item-add-to-collection').click()
        await page.getByTestId('item-collection-choice').selectOption(String(collection.id))
        await page.getByTestId('item-collection-submit').click()

        await page.goto('/station/inventory/collections')
        await page.getByTestId('collection-entry').filter({hasText: collectionName}).click()
        await expect(page.getByTestId('collection-line')).toHaveCount(1)
    })
})
