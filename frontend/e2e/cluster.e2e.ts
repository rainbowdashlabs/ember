/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {MADE_BY_A_STORY} from './fixtures/cluster'
import {
    test, expect, enterCluster, clustersOf, theSeededCluster, apiHeaders, demoAccounts, pageAsThrowaway,
} from './fixtures/auth'
import {activeSidebarGroup, sidebarEntry} from './fixtures/sidebar'

/**
 * Identity and context: who reaches the cluster area, what they see once they are in it, and where
 * a cluster deliberately does not appear.
 */
test.describe('Cluster', () => {
    /**
     * CLS-1 - An instance administrator creates a cluster.
     *
     * A cluster arrives with a station of its own, and the second half of the story is about that
     * station being nobody's: it must not turn up where the instance lists the stations it runs.
     */
    test('an instance administrator creates a cluster', async ({adminPage: page}) => {
        const name = `${MADE_BY_A_STORY}Kreisverband ${test.info().workerIndex}-${Date.now()}`

        await page.goto('/admin/clusters')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByPlaceholder('z.B. Kreisverband Musterstadt').fill(name)
        await page.getByRole('button', {name: 'Erstellen'}).click()

        await expect(page.getByText(name)).toBeVisible()

        await page.goto('/admin/stations')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText(name, {exact: true})).toHaveCount(0)
    })

    /** CLS-2 - A cluster member reaches the cluster space. */
    test('a cluster member reaches the cluster space', async ({adminPage: page}) => {
        await page.goto('/cross-station')
        // Which cluster the shell is acting for is named first, because the demo administrator is
        // appointed to every cluster the other stories build and the button would otherwise open
        // whichever of them was made last
        const cluster = await enterCluster(page)
        await expect(page.getByRole('button', {name: 'Verband'}).first()).toBeVisible()

        await page.getByRole('button', {name: 'Verband'}).first().click()
        await page.waitForURL(/\/cluster$/)

        await expect(page.getByText(cluster.name).first()).toBeVisible()
    })

    /**
     * CLS-4 - An account in no cluster is offered none.
     *
     * The switcher and the panel button hang off the same question, so a member who belongs to no
     * cluster should find neither anywhere in the shell.
     */
    test('an account in no cluster is offered none', async ({memberPage: page, browser, request}) => {
        await page.goto('/station/members/list')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByRole('button', {name: 'Verband'})).toHaveCount(0)

        // And running the station is no different from being at it. A membership in the association is
        // only ever written by somebody who already acts for it, so nothing anybody holds at a station,
        // up to and including administering it, puts them in the association above it.
        const accounts = await demoAccounts(request)
        const manager = accounts.find(account =>
            !!account.email
            && !!account.stationId
            && (account.permissions.includes('STATION_ADMINISTRATOR')
                || account.permissions.includes('STATION_MANAGER'))
            && !(account.clusterPermissions ?? []).length)
        expect(manager, 'somebody runs a station without also having a job at the association').toBeTruthy()
        const running = await pageAsThrowaway(browser, request, [], manager!)
        const headers = await apiHeaders(running)
        const theirs = await running.request.get('/api/v1/clusters', {headers}).then(r => r.json())
        expect(theirs, 'somebody who runs a member station is not thereby in the association').toEqual([])

        await running.goto('/station/members/list')
        await expect(running.getByTestId('app-shell')).toBeVisible()
        await expect(running.getByRole('button', {name: 'Verband'})).toHaveCount(0)
        await running.context().close()
    })

    /** CLS-5 - The cluster space is refused without membership. */
    test('the cluster space is refused without membership', async ({memberPage: page}) => {
        await page.goto('/cluster')

        await expect(page).not.toHaveURL(/\/cluster$/)
    })

    /**
     * CLS-6 - The home station is invisible everywhere a station is listed.
     *
     * A cluster keeps its things on a station of its own, and that station is not one anybody joins,
     * browses or finds. The story asks the three places a station is otherwise offered.
     */
    test('the home station is invisible everywhere a station is listed', async ({adminPage: page}) => {
        const cluster = await theSeededCluster(page)

        // The directory does carry the cluster's name, as the heading its stations are gathered
        // under. What it must not carry is the cluster's own station as an entry of its own.
        await page.goto('/discovery')
        await expect(page.getByRole('heading', {name: cluster.name})).toBeVisible()
        await expect(page.getByRole('link', {name: cluster.name})).toHaveCount(0)

        const sitemap = await page.request.get('/sitemap.xml')
        expect(sitemap.ok()).toBeTruthy()
        expect(await sitemap.text()).not.toContain(cluster.name)

        await page.goto('/cross-station')
        await expect(page.getByText(cluster.name, {exact: true})).toHaveCount(0)
    })

    /**
     * The cluster's own station is not one anybody belongs to.
     *
     * Writing for a cluster leaves a byline on that station so an article can name its author. CLS-6 asks
     * that the shell is invisible in the directory, the sitemap and the picker; this asks the other half,
     * that it never turns up in the list of stations an account is told are theirs.
     */
    test('the home station is in nobody\'s list of their own stations', async ({adminPage: page}) => {
        const cluster = await theSeededCluster(page)
        const headers = await apiHeaders(page)

        const mine = await page.request.get('/api/v1/session/stations', {headers}).then(r => r.json())
        expect(mine.map((s: {stationName: string}) => s.stationName),
            'the administrator writes for the cluster and still belongs to no station of its')
            .not.toContain(cluster.name)

        const across = await page.request
            .get('/api/v1/session/cross-station-dashboard', {headers})
            .then(r => r.json())
        expect(JSON.stringify(across), 'nor does it stand on the page that gathers them')
            .not.toContain(cluster.name)
    })

    /**
     * CLS-3 - An account in two clusters switches between them.
     *
     * The demo has one cluster, so the story makes the second itself: an administrator creates it and
     * appoints themselves, which is the only way anybody ever gets into a new one.
     */
    test('an account in two clusters switches between them', async ({adminPage: page}) => {
        const name = `${MADE_BY_A_STORY}Bezirksverband ${test.info().workerIndex}-${Date.now()}`
        const headers = await apiHeaders(page)

        const created = await page.request.post('/api/v1/clusters', {headers, data: {name, description: null}})
        expect(created.ok()).toBeTruthy()
        const second = await created.json()

        const me = await page.request.get('/api/v1/session', {headers})
        const accountUid = (await me.json()).account.uid
        const appointed = await page.request.post(`/api/v1/clusters/${second.uid}/administrators`,
            {headers, data: {accountUid}})
        expect(appointed.ok()).toBeTruthy()

        const mine = await clustersOf(page)
        expect(mine.length).toBeGreaterThan(1)
        expect(mine.map(cluster => cluster.name)).toContain(name)

        // The first cluster has member stations and the new one has none, so which cluster the space is
        // showing is readable from the station list rather than from the name in the header alone.
        await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), second.uid)
        await page.goto('/cluster/stations')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByText('Jugendfeuerwehr Nordstadt')).toHaveCount(0)

        const first = await theSeededCluster(page)
        await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), first.uid)
        await page.goto('/cluster/stations')
        await expect(page.getByText('Jugendfeuerwehr Nordstadt')).toBeVisible()
    })

    /**
     * CLS-7 - A cluster-only account lands in its cluster after login.
     *
     * Somebody who belongs to no station has no station picker worth showing and no account page worth
     * landing on. The cluster is their whole reason to be here, so that is where they arrive.
     */
    test('a cluster-only account lands in its cluster', async ({page, request}) => {
        const accounts = await demoAccounts(request)
        const clusterOnly = accounts.find(account =>
            !!account.email
            && !account.stationId
            && !account.instanceAdministrator
            && (account.clusterPermissions ?? []).length > 0)
        expect(clusterOnly, 'the seeder makes somebody who belongs to a cluster and to no station').toBeTruthy()

        // Walked rather than planted, because where a login lands is the whole story: the fixtures put a
        // session in place and never answer the question this asks.
        await page.goto('/login')
        await page.getByRole('button', {name: 'Zustimmen'}).click()
        await page.getByText(`${clusterOnly!.firstName} ${clusterOnly!.lastName}`).first().click()

        await page.waitForURL(/\/cluster$/)
        await expect(page.getByTestId('app-shell')).toBeVisible()

        const [cluster] = await clustersOf(page)
        await expect(page.getByText(cluster.name).first()).toBeVisible()
    })

    /**
     * The cluster area is one shell with a sidebar of its own, and every page in it belongs to the
     * cluster rather than to any station. Reaching them proves the guard and the layout agree.
     */
    test('the cluster pages are reachable from within the cluster', async ({adminPage: page}) => {
        await page.goto('/cross-station')
        const cluster = await enterCluster(page)

        for (const route of ['/cluster', '/cluster/stations', '/cluster/members', '/cluster/settings']) {
            await page.goto(route)
            await expect(page.getByTestId('app-shell')).toBeVisible()
            await expect(page.getByText(cluster.name).first()).toBeVisible()
        }
    })

    /**
     * CLS-100 - The sidebar marks where you are, and marks nothing else.
     *
     * A group decided for itself whether it was active by a prefix written beside it, and in ten places
     * that prefix and the entries in the group disagreed. The association's first group is declared
     * `/cluster`, which every route in the panel begins with, so the one group always lit was the one
     * saying nothing about where you are. Nothing anywhere asserted on highlighting until this.
     */
    test('the sidebar lights the group the page belongs to and no other', async ({adminPage: page}) => {
        await page.goto('/cross-station')
        await enterCluster(page)

        for (const [route, label] of [
            ['/cluster/applications', 'Wachen'],
            ['/cluster/modules', 'Vorgaben'],
            ['/cluster/knowledge', 'Wiki'],
        ] as const) {
            await page.goto(route)
            await expect(page.getByTestId('app-shell')).toBeVisible()

            const lit = activeSidebarGroup(page)
            await expect(lit, `${route} lights exactly one group`).toHaveCount(1, {timeout: 15000})
            await expect(lit, `${route} lights ${label}`).toHaveText(new RegExp(label))
        }
    })

    /**
     * CLS-101 - The wiki is called Wiki and is one click away.
     *
     * The association handed somebody a right called Wikiverwaltung over a screen it called Wissen, and
     * buried that screen with the news and the calendar under a group whose only effect was one more
     * click. The station has said Wiki and shown all three at the top level all along.
     */
    test('the wiki is named Wiki and sits at the top level', async ({adminPage: page}) => {
        await page.goto('/cross-station')
        await enterCluster(page)
        await page.goto('/cluster')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(sidebarEntry(page, 'Inhalte'), 'the level that only added a click is gone').toHaveCount(0)
        for (const label of ['Wiki', 'Neuigkeiten', 'Termine']) {
            await expect(sidebarEntry(page, label), `${label} is an entry of its own`).toBeVisible()
        }

        await sidebarEntry(page, 'Wiki').click()
        await expect(page).toHaveURL(/\/cluster\/knowledge$/)
        await expect(activeSidebarGroup(page)).toHaveText(/Wiki/)
    })
})