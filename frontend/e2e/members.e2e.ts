/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, stationPeers} from './fixtures/auth'
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

    /**
     * Somebody the station still has something with cannot simply be written off — equipment in
     * their hands, profiles in their care. The page refuses and says what stands in the way, and
     * the reason being given is the part worth holding.
     */
    test('a member with something outstanding cannot be marked former', async ({managerPage: page, request}) => {
        const {member} = await stationPeers(request)

        await page.goto('/station/members/list')
        await page.getByPlaceholder(/Suche/).first().fill(member.lastName)
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/\d+/)

        await page.getByRole('button', {name: 'Als ehemalig markieren'}).click()

        await expect(page.getByText('Mitglied als ehemalig markieren')).toBeVisible()
        await expect(page.getByText('Dieses Mitglied kann derzeit nicht als ehemalig markiert werden:')).toBeVisible()
        // What stands in the way differs from person to person — equipment they hold, profiles they
        // look after — so the story holds the page to naming something rather than to one reason.
        await expect(page.getByRole('listitem').first()).toBeVisible()
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
