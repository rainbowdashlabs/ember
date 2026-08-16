/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * The creation wizard walks several steps before it writes anything, and each one has to be
 * carried past on its own — which is the point of the story: a member created through it appears
 * in the list afterwards.
 */
test.describe('Members', () => {
    test('a member is created through the wizard', async ({managerPage: page}) => {
        const surname = unique('Story')

        await page.goto('/station/members/create')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByRole('button', {name: 'Weiter'}).first().click()

        await page.getByPlaceholder('Vorname').fill('Testperson')
        await page.getByPlaceholder('Nachname').fill(surname)
        await page.getByPlaceholder('E-Mail-Adresse').fill(`${surname.toLowerCase()}@example.test`)
        await page.getByRole('button', {name: 'Weiter'}).first().click()

        for (let step = 0; step < 4; step += 1) {
            const next = page.getByRole('button', {name: /Weiter|Konto erstellen|Erstellen/}).first()
            if (!await next.isVisible().catch(() => false)) break
            await next.click()
        }

        await page.goto('/station/members/list')
        await expect(page.getByText(surname).first()).toBeVisible()
    })

    test('the member list shows the station and filters by name', async ({managerPage: page}) => {
        await page.goto('/station/members/list')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        const rows = page.getByRole('row')
        await expect(rows.first()).toBeVisible()

        const before = await rows.count()
        await page.getByPlaceholder(/Suche/).first().fill('zzzz-kein-treffer')
        await expect(async () => {
            expect(await rows.count()).toBeLessThan(before)
        }).toPass()
    })

    /**
     * A permission is not a hidden button: the page has to be unreachable for someone without it,
     * which is what stops a guessed URL from working.
     */
    test('a member without the right cannot open the member list', async ({memberPage: page}) => {
        await page.goto('/station/members/list')

        await expect(page.getByRole('table')).toHaveCount(0)
    })
})
