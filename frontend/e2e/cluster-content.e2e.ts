/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, clusterHeaders, enterCluster, clusterStationManager, pageAsThrowaway}
    from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'

/**
 * What a cluster writes once and every station under it reads.
 *
 * Nothing is copied: the cluster keeps its things on a station of its own, its member stations are
 * connected to that station, and what they read is the article the cluster wrote. That is why every
 * assertion is about the federated surfaces rather than about a station's own lists, and why the cluster
 * is named as the source rather than the station.
 */
test.describe('Cluster content', () => {
    /**
     * CLS-31 - A cluster knowledge base article reaches every member station.
     *
     * Read at the station over the connection it already has. There is no page of federated articles of
     * their own: they arrive in the station's own wiki, which is the point.
     */
    test('a cluster article reaches the member stations', async ({adminPage: page, browser, request}) => {
        const cluster = await enterCluster(page)
        const headers = await clusterHeaders(page, cluster)
        const name = `Dienstanweisung ${test.info().workerIndex}-${Date.now()}`

        // Written the way the screens write it: the station's own knowledge base, over the cluster's station
        const written = await page.request.post('/api/v1/kb/files/markdown', {
            headers,
            data: {folderId: null, name, description: 'Vom Verband', content: '# Gilt für alle'},
        })
        expect(written.ok()).toBeTruthy()

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))

        // Read on the station's own wiki, which is where somebody at the station would look for it.
        // Shared articles are shown by default, so nothing has to be switched on first.
        await station.goto('/station/knowledge')
        await expect(station.getByTestId('app-shell')).toBeVisible()

        // Once, however many shares reach it, and badged with the association rather than a station
        const entry = station.getByTestId('kb-item').filter({hasText: name})
        await expect(entry).toHaveCount(1, {timeout: 15000})
        await expect(entry).toContainText(cluster.name)

        await station.context().close()
    })

    /**
     * CLS-32 - Cluster news reaches the member stations.
     *
     * Listed with the cluster as its source rather than as something the station wrote itself.
     */
    test('cluster news reaches the member stations', async ({adminPage: page, browser, request}) => {
        const cluster = await enterCluster(page)
        const headers = await clusterHeaders(page, cluster)
        const title = `Kreismitteilung ${test.info().workerIndex}-${Date.now()}`

        const written = await page.request.post('/api/v1/news', {
            headers,
            data: {title, contentMarkdown: 'Alle Wachen sind gemeint.'},
        })
        expect(written.ok()).toBeTruthy()

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))

        // In the station's own news list, among what the station wrote itself, and sent by the association
        await station.goto('/station/news')
        await expect(station.getByTestId('app-shell')).toBeVisible()
        await expect(station.getByText(title).first()).toBeVisible({timeout: 15000})
        await expect(station.getByText(cluster.name).first()).toBeVisible()

        await station.context().close()
    })

    /**
     * CLS-33 - A member registers for a cluster event.
     *
     * The appointment is the cluster's and the registration is the member's, and both ends read the same
     * one because nothing was copied.
     */
    test('a cluster appointment reaches the member stations', async ({adminPage: page, browser, request}) => {
        const cluster = await enterCluster(page)
        const headers = await clusterHeaders(page, cluster)
        const name = `Kreisübung ${test.info().workerIndex}-${Date.now()}`
        const start = new Date(Date.now() + 14 * 24 * 3600 * 1000).toISOString()
        const end = new Date(Date.now() + 14 * 24 * 3600 * 1000 + 3600 * 1000).toISOString()

        const made = await page.request.post('/api/v1/events', {
            headers,
            data: {
                name,
                description: 'Gemeinsame Übung',
                eventType: 'ONE_TIME',
                startTime: start,
                endTime: end,
                requiresRegistration: true,
            },
        })
        expect(made.ok()).toBeTruthy()

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))

        // The member's own list of what is coming up, which is where a registration is actually made
        await station.goto('/station/events/upcoming')
        await expect(station.getByTestId('app-shell')).toBeVisible()

        const tile = station.getByTestId('federated-event').filter({hasText: name})
        await expect(tile).toBeVisible({timeout: 15000})
        await expect(tile.getByText(cluster.name)).toBeVisible()

        // Registering is the member's act and the appointment is the association's: one row, both ends
        await tile.getByTestId('federated-event-register').click()
        await expect(tile.getByTestId('federated-event-registration')).toBeVisible({timeout: 15000})

        await station.context().close()
    })

    /**
     * CLS-34 - A released station stops seeing cluster content.
     *
     * Released on a station the story makes: letting a seeded member station go would take the subject of
     * every other cluster story with it.
     */
    test('a released station stops seeing cluster content', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Inhaltsverband')
        const title = `Rundschreiben ${Date.now()}`

        const written = await page.request.post('/api/v1/news', {
            headers: own.contentHeaders,
            data: {title, contentMarkdown: 'Für alle.'},
        })
        expect(written.ok()).toBeTruthy()

        // On the station's own news list while it still answers to the cluster
        await own.stationPage.goto('/station/news')
        await expect(own.stationPage.getByTestId('app-shell')).toBeVisible()
        await expect(own.stationPage.getByText(title).first()).toBeVisible({timeout: 15000})

        const released = await page.request.delete(`/api/v1/cluster/stations/${own.stationUid}`,
            {headers: own.headers})
        expect(released.ok()).toBeTruthy()

        // And gone from it afterwards. The page still works; what went is the cluster's half of it.
        await own.stationPage.reload()
        await expect(own.stationPage.getByTestId('app-shell')).toBeVisible()
        await expect(own.stationPage.getByText(title)).toHaveCount(0, {timeout: 15000})
    })
})
