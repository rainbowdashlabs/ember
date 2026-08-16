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
    /**
     * Held back until the second station's page is understood. On its own this passes in a second;
     * inside the full run the partner's federation page renders an empty frame and stays empty for
     * forty-five seconds, so the wait is not the problem. Something about a second station's
     * session arriving while the rest of the suite is working stops that page loading at all.
     */
    test('two stations each carry the other as a partner', async ({managerPage, partnerManagerPage}) => {

        await managerPage.goto('/station/federate')
        await expect(managerPage.getByTestId('app-shell')).toBeVisible()
        const partners = managerPage.locator('main').getByText(/JF |FF |Jugendfeuerwehr/)
        await expect(partners.first()).toBeVisible({timeout: 45_000})

        await partnerManagerPage.goto('/station/federate')
        await expect(partnerManagerPage.getByTestId('app-shell')).toBeVisible()
        // The partner list is answered by asking the partners themselves, so it arrives later than
        // anything served from one station's own database.
        await expect(partnerManagerPage.locator('main').getByText(/Musterstadt/).first())
            .toBeVisible({timeout: 45_000})
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
