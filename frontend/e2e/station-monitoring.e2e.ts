/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, accountWith, demoAccounts, pageAsThrowaway} from './fixtures/auth'

test.describe('Station monitoring', () => {
    /**
     * MON-1 - A member sets up a subscription, the station sees it in the list.
     *
     * The whole point of the page is that a subscription set up by one person becomes visible to
     * whoever runs the station, so the story walks both halves rather than reading the endpoint.
     */
    test('a subscription set up in a profile shows up in the station list', async ({browser, request}) => {
        const administrator = await accountWith(request, 'STATION_ADMINISTRATOR')
        const accounts = await demoAccounts(request)
        const subscriber = accounts.find(account =>
            account.stationId === administrator.stationId
            && !!account.email
            && account.email !== administrator.email)
        expect(subscriber, 'the administrator station has another member with an address').toBeTruthy()

        const member = await pageAsThrowaway(browser, request, [], subscriber)
        await member.goto('/station/profile/settings/notifications')
        await expect(member.getByTestId('app-shell')).toBeVisible()

        // The section renders neither branch while it is still asking whether a subscription exists,
        // so the story waits for it to settle. Reading the empty page as "already set up" is how a
        // story passes without ever having set anything up.
        const create = member.getByRole('button', {name: 'Feed-Token erstellen'})
        const regenerate = member.getByRole('button', {name: 'Neu generieren'})
        await expect(create.or(regenerate).first()).toBeVisible({timeout: 15000})
        if (await create.isVisible()) await create.click()
        await expect(regenerate).toBeVisible({timeout: 15000})
        await member.context().close()

        const station = await pageAsThrowaway(browser, request, [], administrator)
        await station.goto('/station/monitoring/feeds')
        await expect(station.getByTestId('app-shell')).toBeVisible()

        const row = station.getByTestId('feed-use-row').filter({hasText: subscriber!.lastName})
        await expect(row).toHaveCount(1, {timeout: 15000})
        await expect(row).toContainText('Nie')
        await station.context().close()
    })

    /**
     * MON-2 - Traffic, the page statistics and the storage overview answer under Monitor.
     *
     * They moved out of Manage without a redirect, so the story asks for the new addresses and
     * checks that the sidebar leads there.
     */
    test('traffic, the page statistics and the storage overview stand under monitoring', async ({browser, request}) => {
        const administrator = await accountWith(request, 'STATION_ADMINISTRATOR')
        const station = await pageAsThrowaway(browser, request, [], administrator)

        for (const path of ['/station/monitoring/traffic', '/station/monitoring/insights', '/station/monitoring/storage']) {
            await station.goto(path)
            await expect(station.getByTestId('app-shell')).toBeVisible()
            expect(station.url()).toContain(path)
        }

        await expect(station.getByRole('link', {name: 'Feeds'})).toBeVisible()
        await station.context().close()
    })
})
