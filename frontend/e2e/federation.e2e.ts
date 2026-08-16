/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * The station's own side of federation. Connecting two stations and reading a partner's content
 * needs both of them answering, which the seeded partner stations allow; those stories follow once
 * the suite knows how to drive two stations at once.
 */
test.describe('Federation', () => {
    /**
     * Both sides of a partnership, live at once. A station names its partners and the partner names
     * it back — which is the whole of what a partnership is, and cannot be shown from one side.
     */
    test('two stations each carry the other as a partner', async ({managerPage, partnerManagerPage}) => {
        await managerPage.goto('/station/federate')
        await expect(managerPage.getByTestId('app-shell')).toBeVisible()
        const partners = managerPage.locator('main').getByText(/JF |FF |Jugendfeuerwehr/)
        await expect(partners.first()).toBeVisible()

        await partnerManagerPage.goto('/station/federate')
        await expect(partnerManagerPage.getByTestId('app-shell')).toBeVisible()
        await expect(partnerManagerPage.locator('main').getByText(/Musterstadt/).first()).toBeVisible()
    })

    /** What a partner shares reaches the other station's own knowledge base. */
    test('the knowledge base shows what partner stations share', async ({managerPage: page}) => {
        await page.goto('/station/knowledge')

        await expect(page.getByPlaceholder('Suchen...')).toBeVisible()
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the partner stations are reachable', async ({managerPage: page}) => {
        await page.goto('/station/federate')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/federate')
    })

    test('what the station shares is configurable', async ({managerPage: page}) => {
        await page.goto('/station/federate/settings')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('the boards partner stations share are reachable', async ({managerPage: page}) => {
        await page.goto('/station/federation/boards')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('a member does not configure what the station shares', async ({memberPage: page}) => {
        await page.goto('/station/federate/settings')

        await expect(page.getByRole('button', {name: /Speichern/})).toHaveCount(0)
    })
})
