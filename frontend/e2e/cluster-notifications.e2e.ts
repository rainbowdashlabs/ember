/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {ownCluster, type OwnCluster} from './fixtures/cluster'
import {sidebar} from './fixtures/sidebar'

/**
 * What an association is told, under the bell in its own menu.
 *
 * The association's notices used to be written and never shown: the station's inbox reads the station's feed,
 * and nothing read the association's. A station asking to join is the notice every association gets first, so
 * that is what these stories send.
 *
 * Every story builds an association of its own, because a request to join is about one station and one
 * association, and the seeded one has other workers deciding about its stations at the same moment.
 */
test.describe('Cluster notifications', () => {
    /** One of the association's screens, entered the way the switcher enters it. */
    async function clusterScreen(page: Page, own: OwnCluster, path: string) {
        await page.goto(path)
        await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
        await page.goto(path)
        await expect(page.getByTestId('app-shell')).toBeVisible()
    }

    /**
     * The station of the story lets go of the association and asks to come back, which is the one way an
     * association with a station of its own is asked anything.
     */
    async function askToJoinAgain(page: Page, own: OwnCluster) {
        const released = await page.request.delete(`/api/v1/cluster/stations/${own.stationUid}`,
            {headers: own.headers})
        expect(released.ok(), `the station was let go (${await released.text()})`).toBeTruthy()

        const applied = await own.stationPage.request.post('/api/v1/station/cluster/applications',
            {headers: await apiHeaders(own.stationPage), data: {clusterUid: own.uid}})
        expect(applied.ok(), `and asked to come back (${await applied.text()})`).toBeTruthy()
    }

    /** The bell in the association's menu, with whatever count it carries. */
    function bell(page: Page) {
        return sidebar(page).getByRole('link', {name: /^Benachrichtigungen( \d+)?$/})
    }

    /**
     * The bell counts what is waiting, the inbox lists it, and opening it lands on the association's page the
     * notice is about, where the request is waiting to be decided.
     */
    test('an association notice is counted under the bell and opens the association page it is about',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'Glocke')
            await askToJoinAgain(page, own)

            await expect(async () => {
                await clusterScreen(page, own, '/cluster')
                await expect(bell(page), 'the bell counts the one notice').toHaveAccessibleName(
                    'Benachrichtigungen 1', {timeout: 2_000})
            }).toPass({timeout: 30_000})

            await bell(page).click()
            await expect(page).toHaveURL(/\/cluster\/notifications$/)

            const notice = page.getByTestId('notification-entry')
                .filter({hasText: `${own.stationName} möchte dem Verband beitreten`})
            await expect(notice).toBeVisible()
            await expect(notice).toContainText('Neue Beitrittsanfrage')

            await notice.click()
            await expect(page).toHaveURL(/\/cluster\/applications$/)
            await expect(page.getByText(own.stationName, {exact: true}), 'the request waits on the page it opened')
                .toBeVisible()

            await expect(bell(page), 'and opening it read it').toHaveAccessibleName('Benachrichtigungen')
            await page.reload()
            await expect(bell(page), 'which the server remembers').toHaveAccessibleName('Benachrichtigungen')

            await own.stationPage.context().close()
        })
})
