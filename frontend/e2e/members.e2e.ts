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

    /**
     * Somebody the station still has something with cannot simply be written off — equipment in
     * their hands, profiles in their care. The page refuses and says what stands in the way, and
     * the reason being given is the part worth holding.
     *
     * The story looks for such a person rather than naming one: who holds what changes as the rest
     * of the suite hands equipment out and takes it back, and a story that insists on one member
     * would be testing the seeder's mood.
     */
    test('a member with something outstanding cannot be marked former', async ({managerPage: page}) => {
        const warning = page.getByText('Dieses Mitglied kann derzeit nicht als ehemalig markiert werden:')

        const rows = page.getByTestId('member-row')

        await page.goto('/station/members/list')
        await expect(rows.first()).toBeVisible()

        for (let index = 0; index < Math.min(await rows.count(), 6); index += 1) {
            if (index > 0) {
                await page.goto('/station/members/list')
                await expect(rows.first()).toBeVisible()
            }

            await rows.nth(index).getByRole('button', {name: 'Details'}).click()
            await page.waitForURL(/\/station\/members\/detail\/\d+/)

            const mark = page.getByRole('button', {name: 'Als ehemalig markieren'})
            const offered = await mark.waitFor({state: 'visible', timeout: 5_000}).then(() => true, () => false)
            if (!offered) continue

            await mark.click()
            await expect(page.getByText('Mitglied als ehemalig markieren')).toBeVisible()

            const refused = await warning.waitFor({state: 'visible', timeout: 5_000}).then(() => true, () => false)
            if (!refused) continue

            // What stands in the way differs from person to person — equipment they hold, profiles
            // they look after — so the story holds the page to naming something rather than to one
            // reason.
            await expect(page.getByRole('listitem').first()).toBeVisible()
            return
        }

        throw new Error('No member of the station had anything outstanding to be held back by')
    })

    /**
     * Somebody who joined and left again without ever taking anything out has nothing standing in
     * the way, so the same button confirms rather than warns — and they leave the active list.
     */
    test('a member with nothing outstanding is marked former', async ({managerPage: page}) => {
        const surname = unique('Abschied')

        await page.goto('/station/members/create')
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
        await page.getByPlaceholder(/Suche/).first().fill(surname)
        await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
        await page.waitForURL(/\/station\/members\/detail\/\d+/)

        await page.getByRole('button', {name: 'Als ehemalig markieren'}).first().click()
        await expect(page.getByText('Mitglied als ehemalig markieren')).toBeVisible()
        await page.getByRole('button', {name: 'Als ehemalig markieren'}).nth(1).click()

        await page.goto('/station/members/former')
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
