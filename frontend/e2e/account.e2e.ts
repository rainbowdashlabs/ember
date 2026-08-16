/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, accountWithout, pageAsThrowaway, stationPeers} from './fixtures/auth'

/**
 * Logging in is the one story that walks the login screen itself; every other story in the suite
 * takes its session from the fixture instead.
 *
 * Logging out ends a session, and the stored sessions are shared by the whole suite — so that story
 * takes an account nobody else is using. Ending someone else's session mid-run makes every member story in
 * flight fail at once, and looks exactly like flakiness.
 */

test.describe('Account & session', () => {
    /**
     * The consent gate comes first on a fresh browser and the one-click accounts only appear behind
     * it, which is the order a real first visit meets them too.
     */
    test('logging in reaches the station', async ({page, request}) => {
        const account = await accountWithout(request, 'MEMBER', 'STATION_ADMINISTRATOR')

        await page.goto('/login')
        await page.getByRole('button', {name: 'Zustimmen'}).click()

        await page.getByText(`${account.firstName} ${account.lastName}`).first().click()

        await page.waitForURL(url => !url.pathname.startsWith('/login'))
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    test('an unauthenticated visitor is sent to the login', async ({page}) => {
        await page.goto('/login')
        await page.evaluate(() => window.localStorage.clear())

        await page.goto('/station/members/list')

        await expect(page).toHaveURL(/\/login/)
    })

    test('logging out ends the session', async ({browser, request}) => {
        const {member} = await stationPeers(request)
        const page = await pageAsThrowaway(browser, request, [member.email])

        await page.goto('/station/dashboard/overview')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByTestId('account-menu').click()
        await page.getByRole('button', {name: 'Abmelden'}).click()

        await page.waitForURL(/\/login/)
        await page.goto('/station/dashboard/overview')
        await page.waitForURL(/\/login/)
    })
})
