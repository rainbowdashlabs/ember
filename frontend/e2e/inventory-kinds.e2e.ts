/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import type {Page} from '@playwright/test'

/** One inventory as the summary endpoint reports it. */
interface Summary {
    id: number
    name: string
    homogeneous: boolean
    artCount: number
}

/** The inventories of the signed-in station, read the way the manage screen reads them. */
async function summaries(page: Page): Promise<Summary[]> {
    const headers = await apiHeaders(page)
    return page.request.get('/api/v1/inventories/summary', {headers}).then(r => r.json())
}

/**
 * The seeded collection: two kinds are defined in it and four pieces lie in it carrying none, which
 * is what makes it the one inventory worth counting kinds on.
 */
async function theSeededCollection(page: Page): Promise<Summary> {
    const found = (await summaries(page)).find(entry => entry.name === 'Gemeindematerial')
    expect(found, 'the demo data holds the mixed inventory this story is about').toBeTruthy()
    return found as Summary
}

test.describe('The two kinds of inventory', () => {
    /**
     * The badges are what a list of a dozen inventories is read by. The count of kinds is the one
     * that has to be right for the wrong reason: it counts what is defined, so the four pieces
     * lying in the box without a kind must not lower it.
     */
    test('an inventory wears its owner, its sizes and its kind, and a collection counts its kinds',
        async ({managerPage: page}) => {
            const collection = await theSeededCollection(page)
            expect(collection.homogeneous, 'the seeded box holds different things').toBeFalsy()
            expect(collection.artCount, 'and two kinds are defined in it').toBe(2)

            await page.goto('/station/inventory/manage')
            const card = page.getByTestId('inventory-card').filter({hasText: 'Gemeindematerial'})
            await expect(card).toBeVisible()

            await expect(card.getByTestId('inventory-badge-kind'), 'it is named a collection')
                .toHaveText('Sammlung')
            await expect(card.getByTestId('inventory-badge-type'), 'and says whose it is')
                .toHaveText('Extern')
            await expect(card.getByTestId('inventory-badge-arts'), 'and how many kinds are defined in it')
                .toHaveText('2 Arten')
            await expect(card.getByTestId('inventory-badge-sizes'),
                'it keeps no sizes, and says nothing rather than claiming none').toHaveCount(0)

            const stock = (await summaries(page)).find(entry => entry.homogeneous)
            expect(stock, 'the demo data also holds a stock').toBeTruthy()
            const stockCard = page.getByTestId('inventory-card').filter({hasText: stock!.name})
            await expect(stockCard.getByTestId('inventory-badge-kind')).toHaveText('Vorrat')
            await expect(stockCard.getByTestId('inventory-badge-arts'),
                'a stock holds one thing, so a count of kinds would be noise').toHaveCount(0)
        })

    /**
     * An inventory is created by naming which of the two it is rather than by ticking a box, and the
     * name it was given is what the list shows it as afterwards.
     */
    test('a new inventory is created as a collection by name', async ({managerPage: page}) => {
        const name = `Funkkiste ${Date.now()}`

        await page.goto('/station/inventory/manage')
        await page.getByTestId('create-inventory').click()
        await page.getByTestId('inventory-name').fill(name)
        await page.getByTestId('inventory-kind').selectOption('COLLECTION')
        await page.getByRole('button', {name: 'Speichern'}).click()

        const card = page.getByTestId('inventory-card').filter({hasText: name})
        await expect(card).toBeVisible()
        await expect(card.getByTestId('inventory-badge-kind')).toHaveText('Sammlung')

        const written = (await summaries(page)).find(entry => entry.name === name)
        expect(written?.homogeneous, 'and it was written down as a collection').toBe(false)
    })

    /**
     * The way from the stock list into the settings of the same inventory and back again. The point
     * is that the return lands on the inventory somebody came from rather than on the list of all of
     * them.
     */
    test('the stock list leads into the settings of its own inventory and back',
        async ({managerPage: page}) => {
            const collection = await theSeededCollection(page)

            await page.goto(`/station/inventory/detail/${collection.id}`)
            await expect(page.getByTestId('inventory-detail-edit')).toBeVisible()
            await page.getByTestId('inventory-detail-edit').click()

            await expect(page).toHaveURL(new RegExp(`/station/inventory/edit/${collection.id}$`))

            await page.getByTestId('inventory-edit-back').click()
            await expect(page, 'and back on the inventory it came from')
                .toHaveURL(new RegExp(`/station/inventory/detail/${collection.id}$`))
        })

    /** Somebody who may not change an inventory is not offered the way into its settings. */
    test('a member is not offered the way into the settings', async ({memberPage: page}) => {
        await page.goto('/station/inventory/my')
        await expect(page.getByTestId('inventory-detail-edit')).toHaveCount(0)
    })

    /**
     * A kind carries its own attributes, and they are written down on the kind. This is the walk the
     * owner could not find: the colour of a radio belongs to the kind Funkgerät, not to a screen
     * somewhere else that asks what the field is for.
     */
    test('a field is written on the kind it describes', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()
        const box = await page.request.post('/api/v1/inventories', {
            headers,
            data: {name: `Funkkiste ${stamp}`, inventoryType: 'INTERNAL', hasSizes: false, homogeneous: false},
        }).then(r => r.json())
        const art = await page.request.post(`/api/v1/inventories/${box.id}/arts`, {
            headers,
            data: {name: 'Funkgerät', note: '', position: 0},
        }).then(r => r.json())

        await page.goto(`/station/inventory/edit/${box.id}`)
        await page.getByTestId('art-row-Funkgerät').getByRole('button', {name: 'Bearbeiten'}).click()

        const panel = page.getByTestId('modal').getByTestId('inventory-fields')
        await expect(panel).toBeVisible()
        await panel.getByTestId('add-field').click()
        await panel.getByTestId('field-label').fill('Farbe')
        await panel.getByTestId('field-type').selectOption('ENUM')
        await panel.getByTestId('field-save').click()

        await expect(async () => {
            const fields = await page.request.get(`/api/v1/inventories/${box.id}/fields`, {headers})
                .then(r => r.json())
            const written = fields.find((f: {label: string}) => f.label === 'Farbe')
            expect(written, 'the field was written down').toBeTruthy()
            expect(written.artId, 'and it belongs to the kind it was written on').toBe(art.id)
            expect(written.itemId, 'and to no single piece').toBeFalsy()
        }).toPass()
    })

    /**
     * The fields of the inventory itself and the fields of a kind no longer share one list. Standing
     * on the inventory shows only what every piece in it carries, which is what makes the fields of
     * a kind findable at the kind.
     */
    test('the inventory screen shows only the fields of the inventory itself',
        async ({managerPage: page}) => {
            const collection = await theSeededCollection(page)

            await page.goto(`/station/inventory/edit/${collection.id}`)
            const panel = page.getByTestId('inventory-fields').first()
            await expect(panel).toBeVisible()
            await expect(panel.getByText('Rufname'),
                'the call sign hangs on a kind and is edited there').toHaveCount(0)
        })

    /** An inventory is reached by typing part of its name, and the palette says which kind it is. */
    test('the quick search finds an inventory and names its kind', async ({managerPage: page}) => {
        await page.goto('/station/dashboard/overview')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.keyboard.press('Control+k')
        const palette = page.getByTestId('quick-search')
        await expect(palette).toBeVisible()

        await palette.getByPlaceholder('Seite, Mitglied, Termin, Inventar oder Wiki suchen…').fill('Gemeindematerial')

        const section = palette.getByTestId('palette-section-inventories')
        await expect(section).toBeVisible()
        const hit = section.getByTestId('palette-result').filter({hasText: 'Gemeindematerial'})
        await expect(hit, 'and it says which of the two kinds it is').toContainText('Sammlung')

        const collection = await theSeededCollection(page)
        await hit.click()
        await expect(page).toHaveURL(new RegExp(`/station/inventory/detail/${collection.id}$`))
    })
})
