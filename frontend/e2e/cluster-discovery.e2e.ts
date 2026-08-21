/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'

/**
 * How a cluster shows in the public directory, and how it does not.
 *
 * A cluster is a reading aid here and nothing more: its stations are what anybody can visit, and the
 * heading gathering them is not a door of its own.
 */
test.describe('Cluster discovery', () => {
    /**
     * CLS-45 - Public stations of one cluster are shown as a group.
     *
     * Read as an anonymous visitor, because that is who the directory is for.
     */
    test('public stations of one cluster are shown as a group', async ({page, request}) => {
        const entries = await request.get('/api/v1/public/discovery/stations').then(r => r.json())
        const grouped = (Array.isArray(entries) ? entries : entries.stations ?? [])
            .filter((entry: {clusterUid?: string}) => !!entry.clusterUid)
        expect(grouped.length, 'the demo has public stations under a cluster').toBeGreaterThan(0)
        const clusterName: string = grouped[0].clusterName

        await page.goto('/discovery')
        await expect(page.getByRole('heading', {name: clusterName})).toBeVisible()

        // Each station under it still goes to its own page, which is the whole point of the grouping
        for (const entry of grouped.slice(0, 2)) {
            await expect(page.getByText(entry.name).first()).toBeVisible()
        }
    })

    /**
     * CLS-46 - A cluster with nothing public does not appear.
     *
     * A cluster made for this story has one station and it is hidden, so the directory has nothing of it
     * to gather and must not name it anyway.
     */
    test('a cluster with nothing public does not appear', async ({page, adminPage, browser, request}) => {
        const own = await ownCluster(adminPage, browser, request, 'Stillverband')

        await page.goto('/discovery')
        await expect(page.getByRole('heading', {name: own.name})).toHaveCount(0)
        await expect(page.getByText(own.stationName)).toHaveCount(0)
    })

    /**
     * CLS-47 - The cluster has no page of its own.
     *
     * The heading is a heading. Following the cluster anywhere is not offered, and its stations are the
     * only way in.
     */
    test('the cluster heading is not a way in', async ({page, request}) => {
        const entries = await request.get('/api/v1/public/discovery/stations').then(r => r.json())
        const grouped = (Array.isArray(entries) ? entries : entries.stations ?? [])
            .filter((entry: {clusterUid?: string}) => !!entry.clusterUid)
        expect(grouped.length).toBeGreaterThan(0)
        const clusterName: string = grouped[0].clusterName

        await page.goto('/discovery')
        await expect(page.getByRole('heading', {name: clusterName})).toBeVisible()
        await expect(page.getByRole('link', {name: clusterName})).toHaveCount(0)

        const direct = await request.get(`/api/v1/public/clusters/${grouped[0].clusterUid}`)
        expect(direct.ok(), 'a cluster has no public page behind it either').toBeFalsy()
    })
})
