/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, clustersOf} from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'
import {sidebar, sidebarEntry} from './fixtures/sidebar'

/**
 * What a cluster decides on behalf of its stations, and what it deliberately leaves alone.
 *
 * Every story here builds a cluster of its own with a station under it. Governance reaches every member
 * station at once, so denying a module or locking a colour on the seeded cluster would land on stations
 * three other workers are reading at that moment. What is under test is the reach, not which cluster it
 * happened to.
 */
test.describe('Cluster governance', () => {
    /**
     * CLS-17 - A denied module disappears at the station.
     *
     * Nothing the station put in it is deleted, and the toggle it used to flip says who took it away.
     */
    test('a denied module disappears at the station', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Modulverband')

        await own.stationPage.goto('/station/manage/modules')
        await expect(own.stationPage.getByTestId('app-shell')).toBeVisible()
        const toggle = own.stationPage.locator('[data-testid="module-toggle"][data-module="BOARDS"]')
        await expect(toggle.getByRole('switch')).toBeEnabled()

        const denied = await page.request.put('/api/v1/cluster/modules',
            {headers: own.headers, data: {deniedModules: ['BOARDS']}})
        expect(denied.ok()).toBeTruthy()

        await own.stationPage.goto('/station/manage/modules')
        await expect(toggle.getByRole('switch')).toBeDisabled()
        await expect(own.stationPage.getByText(`Vom Verband ${own.name} abgeschaltet`)).toBeVisible()

        await own.stationPage.goto('/station/dashboard/overview')
        await expect(sidebar(own.stationPage)).toBeVisible()
        await expect(sidebarEntry(own.stationPage, 'Boards')).toHaveCount(0)
    })

    /**
     * CLS-18 - Everything not denied stays the station's own choice.
     *
     * A cluster that switches one thing off has not taken the rest, which is the difference between a
     * denial and a takeover.
     */
    test('everything the cluster has not denied stays the station\'s own', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Restverband')

        const denied = await page.request.put('/api/v1/cluster/modules',
            {headers: own.headers, data: {deniedModules: ['BOARDS']}})
        expect(denied.ok()).toBeTruthy()

        await own.stationPage.goto('/station/manage/modules')
        const news = own.stationPage.locator('[data-testid="module-toggle"][data-module="NEWS"]').getByRole('switch')
        await expect(news).toBeEnabled()
        await expect(news).toHaveAttribute('aria-checked', 'true')

        await news.click()
        await expect(news).toHaveAttribute('aria-checked', 'false')
        await news.click()
        await expect(news).toHaveAttribute('aria-checked', 'true')
    })

    /**
     * CLS-19 - A locked look and feel cannot be changed at the station.
     *
     * The lock is the whole of it: an unlocked setting the cluster handed down is a starting point the
     * station may still move, and the screen has to tell the two apart.
     */
    test('a locked look and feel cannot be changed at the station', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Farbverband')

        const colors = JSON.stringify({
            light: {primary: '#123456', primaryAccent: '#123456', secondary: '#123456',
                secondaryAccent: '#123456', info: '#123456', infoAccent: '#123456',
                success: '#123456', error: '#123456'},
            dark: {primary: '#123456', primaryAccent: '#123456', secondary: '#123456',
                secondaryAccent: '#123456', info: '#123456', infoAccent: '#123456',
                success: '#123456', error: '#123456'},
            bgLight: '#eaeaea', bgLightAccent: '#CFCFCF', bgDark: '#212121', bgDarkAccent: '#191919',
        })
        const locked = await page.request.put('/api/v1/cluster/look-and-feel', {
            headers: own.headers,
            data: {
                defaultTheme: 'ember', customThemeColors: colors, defaultFeel: null,
                themeLocked: false, colorsLocked: true, feelLocked: false, logoLocked: true,
            },
        })
        expect(locked.ok()).toBeTruthy()

        await own.stationPage.goto('/station/manage/theme')
        await expect(own.stationPage.getByTestId('app-shell')).toBeVisible()
        await expect(own.stationPage.getByText(own.name, {exact: false}).first()).toBeVisible()

        // The station sends its own colours anyway, which is what a screen cannot stop and the server must
        const stationHeaders = await apiHeaders(own.stationPage)
        const ignored = await own.stationPage.request.put('/api/v1/station/manage', {
            headers: stationHeaders,
            data: {name: own.stationName, defaultTheme: 'ember', customThemeColors: '{"light":{}}'},
        })
        expect(ignored.ok()).toBeTruthy()

        const after = await own.stationPage.request.get('/api/v1/station/manage', {headers: stationHeaders})
        const info = await after.json()
        // Compared as colours rather than as text: the round trip through the database rewrites the
        // spacing and the order of the keys, and neither is what the cluster locked.
        expect(JSON.parse(info.customThemeColors)).toEqual(JSON.parse(colors))
        expect(info.colorsLocked).toBeTruthy()
        expect(info.feelLocked).toBeFalsy()
        expect(info.clusterName).toBe(own.name)
    })

    /**
     * CLS-20 - Quota is handed out of the cluster pool.
     *
     * What a station gets comes out of the whole the instance granted, so the two figures move together.
     */
    test('quota is handed out of the cluster pool', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Speicherverband')
        const headers = await apiHeaders(page)

        const pooled = await page.request.put(`/api/v1/clusters/${own.uid}/storage-pool`,
            {headers, data: {quotaBytes: 100 * 1024 * 1024}})
        expect(pooled.ok()).toBeTruthy()

        const before = await page.request.get('/api/v1/cluster/storage', {headers: own.headers}).then(r => r.json())
        expect(before.poolBytes).toBe(100 * 1024 * 1024)

        const given = await page.request.put(`/api/v1/cluster/storage/stations/${own.stationUid}`,
            {headers: own.headers, data: {quotaBytes: 30 * 1024 * 1024}})
        expect(given.ok()).toBeTruthy()

        const after = await page.request.get('/api/v1/cluster/storage', {headers: own.headers}).then(r => r.json())
        expect(after.handedOut).toBe(before.handedOut + 30 * 1024 * 1024)
        expect(after.stations.find((s: {stationUid: string}) => s.stationUid === own.stationUid).quotaBytes)
            .toBe(30 * 1024 * 1024)

        await page.goto('/cluster/storage')
        await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
        await page.goto('/cluster/storage')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText(own.stationName)).toBeVisible()
    })

    /** CLS-21 - Quota beyond the pool is refused, and says the pool is the limit. */
    test('quota beyond the pool is refused', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Grenzverband')
        const headers = await apiHeaders(page)

        const pooled = await page.request.put(`/api/v1/clusters/${own.uid}/storage-pool`,
            {headers, data: {quotaBytes: 10 * 1024 * 1024}})
        expect(pooled.ok()).toBeTruthy()

        const refused = await page.request.put(`/api/v1/cluster/storage/stations/${own.stationUid}`,
            {headers: own.headers, data: {quotaBytes: 50 * 1024 * 1024}})
        expect(refused.ok()).toBeFalsy()
        expect((await refused.text()).toLowerCase()).toContain('pool')
    })

    /**
     * CLS-22 - A cluster station cannot be exported off the instance.
     *
     * Moving it away would leave the cluster holding a station that is no longer there. What the station
     * exports for its own use is untouched, because that leaves nothing behind.
     */
    test('a cluster station cannot be exported off the instance', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Umzugsverband')
        const headers = await apiHeaders(own.stationPage)

        const refused = await own.stationPage.request.post('/api/v1/station/transfer/create-token', {headers})
        expect(refused.ok()).toBeFalsy()
        expect((await refused.text()).toLowerCase()).toContain('cluster')

        // What the station exports for its own use leaves nothing behind, so it is untouched
        const ownExport = await own.stationPage.request.get('/api/v1/station/members/export', {headers})
        expect(ownExport.status()).not.toBe(400)

        const cluster = await clustersOf(page)
        expect(cluster.length).toBeGreaterThan(0)
    })
})
