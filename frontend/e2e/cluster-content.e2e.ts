/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, clusterHeaders, enterCluster, clusterStationManager, pageAsThrowaway}
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
        const stationHeaders = await apiHeaders(station)
        const shared = await station.request.get('/api/v1/federated/kb', {headers: stationHeaders})
        expect(shared.ok()).toBeTruthy()
        const articles: {title: string; stationName: string}[] = await shared.json()

        const mine = articles.filter(article => article.title === name)
        expect(mine.length, 'the article arrives once, however many shares reach it').toBe(1)
        expect(mine[0].stationName, 'and says the cluster wrote it').toBe(cluster.name)

        await station.goto('/station/knowledge')
        await expect(station.getByTestId('app-shell')).toBeVisible()
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
            data: {title, contentMarkdown: 'Alle Wachen sind gemeint.', contentHtml: '<p>Alle Wachen sind gemeint.</p>'},
        })
        expect(written.ok()).toBeTruthy()

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const stationHeaders = await apiHeaders(station)
        const shared = await station.request.get('/api/v1/federated/news', {headers: stationHeaders})
        expect(shared.ok()).toBeTruthy()

        const entries = JSON.stringify(await shared.json())
        expect(entries).toContain(title)
        expect(entries).toContain(cluster.name)

        await station.goto('/station/news')
        await expect(station.getByTestId('app-shell')).toBeVisible()
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
        const stationHeaders = await apiHeaders(station)
        const shared = await station.request.get('/api/v1/federated/events', {headers: stationHeaders})
        expect(shared.ok()).toBeTruthy()
        expect(JSON.stringify(await shared.json())).toContain(name)

        await station.goto('/station/events')
        await expect(station.getByTestId('app-shell')).toBeVisible()
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
            data: {title, contentMarkdown: 'Für alle.', contentHtml: '<p>Für alle.</p>'},
        })
        expect(written.ok()).toBeTruthy()

        const stationHeaders = await apiHeaders(own.stationPage)
        const before = await own.stationPage.request.get('/api/v1/federated/news', {headers: stationHeaders})
        expect(JSON.stringify(await before.json())).toContain(title)

        const released = await page.request.delete(`/api/v1/cluster/stations/${own.stationUid}`,
            {headers: own.headers})
        expect(released.ok()).toBeTruthy()

        const after = await own.stationPage.request.get('/api/v1/federated/news', {headers: stationHeaders})
        expect(JSON.stringify(await after.json())).not.toContain(title)

        // Its own pages still work; what went is the cluster's half
        await own.stationPage.goto('/station/news')
        await expect(own.stationPage.getByTestId('app-shell')).toBeVisible()
    })
})
