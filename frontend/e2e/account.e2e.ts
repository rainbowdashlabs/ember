/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, accountWithout} from './fixtures/auth'

/**
 * ACC-1 to ACC-3 of the story list. ACC-1 is the one story that walks the login UI itself; every
 * other story in the suite takes its session from the fixture instead.
 */
/**
 * Serial on purpose. These stories log in and out of the same seeded account, and a logout revokes
 * a session another one is holding — the interference is between the stories, not in the app. Every
 * other feature's stories touch their own rows and stay fully parallel.
 */
test.describe.configure({mode: 'serial'})

test.describe('Account & session', () => {
    /**
     * The consent gate comes first on a fresh browser and the one-click accounts only appear behind
     * it, which is the order a real first visit meets them too.
     */
    test('ACC-1 logging in reaches the station', async ({page, request}) => {
        const account = await accountWithout(request, 'MEMBER', 'STATION_ADMINISTRATOR')

        await page.goto('/login')
        await page.getByRole('button', {name: 'Zustimmen'}).click()

        await page.getByText(`${account.firstName} ${account.lastName}`).first().click()

        await page.waitForURL(url => !url.pathname.startsWith('/login'))
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('ACC-2 an unauthenticated visitor is sent to the login', async ({page}) => {
        await page.goto('/login')
        await page.evaluate(() => window.localStorage.clear())

        await page.goto('/station/members/list')

        await expect(page).toHaveURL(/\/login/)
    })

    test('ACC-3 logging out ends the session', async ({memberPage: page}) => {
        await page.goto('/station/dashboard/overview')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByTestId('account-menu').click()
        await page.getByRole('button', {name: 'Abmelden'}).click()

        await page.waitForURL(/\/login/)
        await page.goto('/station/dashboard/overview')
        await page.waitForURL(/\/login/)
    })
})
