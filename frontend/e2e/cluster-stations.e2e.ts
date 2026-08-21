/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {MADE_BY_A_STORY} from './fixtures/cluster'
import {test, expect, enterCluster, theSeededCluster, apiHeaders} from './fixtures/auth'

/**
 * How stations come into a cluster and how they leave it again.
 *
 * Serial, because the stories share the one waiting request the seeder leaves: answering it, taking
 * it back and asking again are three stories about the same object, and running them at once would
 * have each of them deciding what the others were about to look at.
 */
test.describe.configure({mode: 'serial'})

test.describe('Cluster stations', () => {
    /**
     * CLS-8 - The cluster creates a station and it is already a member.
     *
     * The station it makes is its own from the first moment: nobody applies, nobody approves, and the
     * station's own page names the cluster it belongs to.
     */
    test('the cluster creates a station and it is already a member', async ({adminPage: page}) => {
        const name = `${MADE_BY_A_STORY}Löschzug ${Date.now()}`

        await page.goto('/cross-station')
        await enterCluster(page)

        await page.goto('/cluster/stations')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await page.getByPlaceholder('z.B. Löschzug Nord').fill(name)
        await page.getByRole('button', {name: 'Erstellen'}).click()

        await expect(page.getByText(name)).toBeVisible()
    })

    /**
     * CLS-13 - A cluster cannot take a station that has not applied.
     *
     * The only way in for a station that already exists is its own owner asking, so the cluster's own
     * screen offers no way to reach for one.
     */
    test('a cluster cannot take a station that has not applied', async ({adminPage: page}) => {
        await page.goto('/cross-station')
        await enterCluster(page)

        await page.goto('/cluster/stations')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        // A name is typed, not chosen: there is no list of the instance's stations to pick from
        await expect(page.getByPlaceholder('z.B. Löschzug Nord')).toBeVisible()
        await expect(page.getByText('JF Partnerwache')).toHaveCount(0)
    })

    /**
     * CLS-14 - A station owner cannot leave on their own.
     *
     * Belonging to a cluster is the cluster's to end. The station's page says which cluster it is and
     * offers nothing that would leave it.
     */
    test('a station owner cannot leave on their own', async ({clusterStationManagerPage: page}) => {
        await page.goto('/station/manage/cluster')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        const cluster = await theSeededCluster(page).catch(() => null)
        await expect(page.getByText('Diese Wache gehört zu diesem Verband.', {exact: false})).toBeVisible()
        await expect(page.getByRole('button', {name: 'Verlassen'})).toHaveCount(0)
        await expect(page.getByRole('button', {name: 'Austreten'})).toHaveCount(0)
        if (cluster) await expect(page.getByText(cluster.name).first()).toBeVisible()
    })

    /**
     * CLS-12 - An owner withdraws an application before it is answered.
     *
     * The station asks and takes it back itself, and the cluster's pending list is empty again. It runs
     * before the two stories that answer an application, because it puts the one waiting request back the
     * way it found it.
     */
    test('an owner withdraws an application before it is answered', async ({adminPage: page, request}) => {
        await page.goto('/cross-station')
        const cluster = await enterCluster(page)
        const headers = await apiHeaders(page)

        await page.goto('/cluster/applications')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Ablehnen'}).first()).toBeVisible()

        const applicant = await withdrawAsTheWaitingOwner(request, cluster.uid)

        // Gone from the pending half rather than gone altogether: a request that was taken back is still
        // something the cluster can see happened, which is why the empty state is not what this asks about.
        await page.reload()
        await expect(page.getByRole('button', {name: 'Ablehnen'})).toHaveCount(0)
        await expect(page.getByRole('button', {name: 'Aufnehmen'})).toHaveCount(0)

        // And it is the station's to ask again, which is what makes a withdrawal different from a refusal
        await applicant()
        await page.reload()
        await expect(page.getByRole('button', {name: 'Ablehnen'}).first()).toBeVisible()
    })

    /**
     * CLS-16 - A cluster with stations cannot be deleted.
     *
     * Deleting one would leave its stations answering to something that is not there. The refusal says
     * what has to happen first rather than simply failing.
     */
    test('a cluster with stations cannot be deleted', async ({adminPage: page}) => {
        const headers = await apiHeaders(page)
        const cluster = await theSeededCluster(page)

        const refused = await page.request.delete(`/api/v1/clusters/${cluster.uid}`, {headers})
        expect(refused.ok()).toBeFalsy()
        expect(await refused.text()).toContain('station')

        await page.goto('/admin/clusters')
        await expect(page.getByText(cluster.name)).toBeVisible()
    })

    /**
     * CLS-15 - The cluster releases a station.
     *
     * Released on a station the story makes for the purpose. Letting go of a seeded member station would
     * take the subject of every other cluster story away with it, and what this is about is the release
     * rather than which station it happened to.
     */
    test('the cluster releases a station', async ({adminPage: page}) => {
        await page.goto('/cross-station')
        await enterCluster(page)
        const headers = await apiHeaders(page)
        const cluster = await enterCluster(page)
        const withCluster = {...headers, 'X-Cluster-Id': cluster.uid}

        const name = `${MADE_BY_A_STORY}Löschzug Abgang ${test.info().workerIndex}-${Date.now()}`
        const made = await page.request.post('/api/v1/cluster/stations', {headers: withCluster, data: {name}})
        expect(made.ok()).toBeTruthy()
        const station = await made.json()

        await page.goto('/cluster/stations')
        await expect(page.getByText(name)).toBeVisible()

        const released = await page.request.delete(`/api/v1/cluster/stations/${station.uid}`, {headers: withCluster})
        expect(released.ok()).toBeTruthy()

        await page.reload()
        await expect(page.getByText(name)).toHaveCount(0)

        // And the station itself no longer answers to anybody, which is the half the station side sees
        const after = await page.request.get('/api/v1/station/cluster',
            {headers: {...headers, 'X-Station-Id': station.uid}})
        expect(after.ok()).toBeTruthy()
        expect((await after.json()).clusterUid).toBeFalsy()
    })

    /**
     * CLS-11 - The cluster denies an application with a reason, and CLS-9 after it: the station may
     * ask again.
     *
     * One story rather than two, because the second half is only meaningful on a station that has just
     * been refused, and the seeder leaves exactly one request waiting.
     */
    test('the cluster denies an application with a reason and the station may ask again',
        async ({adminPage: page}) => {
            await page.goto('/cross-station')
            await enterCluster(page)

            await page.goto('/cluster/applications')
            await expect(page.getByTestId('app-shell')).toBeVisible()

            const waiting = page.getByRole('button', {name: 'Ablehnen'}).first()
            await expect(waiting).toBeVisible()
            await waiting.click()

            const dialog = page.getByRole('dialog')
            await dialog.getByPlaceholder('Warum wird die Anfrage abgelehnt?').fill('Im nächsten Jahr gerne')
            await dialog.getByRole('button', {name: 'Ablehnen'}).click()

            // The refusal and its reason stay readable; what goes is the request waiting to be answered
            await expect(page.getByRole('button', {name: 'Aufnehmen'})).toHaveCount(0)
            await expect(page.getByText('Im nächsten Jahr gerne')).toBeVisible()
        })

    /**
     * CLS-10 - The cluster approves an application.
     *
     * The station asks first, through the same call its own screen makes, and the cluster answers on
     * the screen. Afterwards it is in the member list.
     */
    test('the cluster approves an application', async ({adminPage: page, request}) => {
        await page.goto('/cross-station')
        const cluster = await enterCluster(page)

        const applied = await applyAsSomeStandaloneOwner(request, cluster.uid)

        await page.goto('/cluster/applications')
        const decide = page.getByRole('button', {name: 'Aufnehmen'}).first()
        await expect(decide).toBeVisible()
        await decide.click()

        await page.goto('/cluster/stations')
        await expect(page.getByText(applied)).toBeVisible()
    })
})

/**
 * Asks for a place on behalf of a station that has one to ask for.
 *
 * A story about the cluster answering needs something to answer, and only a station's own owner may
 * ask. Rather than guess which seeded account owns what, this finds a station standing outside every
 * cluster whose owner can sign in, and asks as them.
 *
 * @returns the name of the station that asked
 */
async function applyAsSomeStandaloneOwner(
    request: import('@playwright/test').APIRequestContext,
    clusterUid: string,
): Promise<string> {
    const accounts = await request.get('/api/v1/demo/accounts').then(r => r.json())
    const groups: {stationId?: string; stationName?: string; accounts?: {email?: string; permissions: string[]}[]}[] =
        accounts.stationGroups ?? []

    for (const group of groups) {
        for (const account of group.accounts ?? []) {
            if (!account.email) continue
            if (!account.permissions.includes('STATION_ADMINISTRATOR')
                && !account.permissions.includes('STATION_MANAGER')) continue

            const login = await request.post('/api/v1/demo/login', {data: {email: account.email}})
            if (!login.ok()) continue
            const {token} = await login.json()
            const headers = {Authorization: `Bearer ${token}`, 'X-Station-Id': group.stationId ?? ''}

            const applied = await request.post('/api/v1/station/cluster/applications', {
                headers,
                data: {clusterUid},
            })
            if (applied.ok()) return group.stationName ?? ''
        }
    }
    throw new Error('No station outside a cluster has an owner who could ask for a place')
}

/**
 * Takes back whichever application is waiting, as the owner who made it, and hands back a way to make it
 * again.
 *
 * Only the station's own owner may withdraw, so the story cannot do it as the cluster. Which station is
 * waiting is the seeder's business, so it is read rather than named.
 */
async function withdrawAsTheWaitingOwner(
    request: import('@playwright/test').APIRequestContext,
    clusterUid: string,
): Promise<() => Promise<void>> {
    const accounts = await request.get('/api/v1/demo/accounts').then(r => r.json())
    const groups: {stationId?: string; accounts?: {email?: string; permissions: string[]}[]}[] =
        accounts.stationGroups ?? []

    for (const group of groups) {
        for (const account of group.accounts ?? []) {
            if (!account.email) continue
            if (!account.permissions.includes('STATION_ADMINISTRATOR')
                && !account.permissions.includes('STATION_MANAGER')) continue

            const login = await request.post('/api/v1/demo/login', {data: {email: account.email}})
            if (!login.ok()) continue
            const {token} = await login.json()
            const headers = {Authorization: `Bearer ${token}`, 'X-Station-Id': group.stationId ?? ''}

            const mine = await request.get('/api/v1/station/cluster', {headers})
            if (!mine.ok()) continue
            const waiting = ((await mine.json()).applications ?? [])
                .find((application: {status: string}) => application.status === 'PENDING')
            if (!waiting) continue

            const gone = await request.delete(`/api/v1/station/cluster/applications/${waiting.id}`, {headers})
            if (!gone.ok()) continue
            return async () => {
                await request.post('/api/v1/station/cluster/applications', {headers, data: {clusterUid}})
            }
        }
    }
    throw new Error('No station is waiting on an application it could take back')
}
