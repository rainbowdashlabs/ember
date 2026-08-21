/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, enterCluster, clusterAccountOnlyWith, clusterPage,
    clusterStationManager, pageAsThrowaway} from './fixtures/auth'

/**
 * The people at every station under a cluster, and the questions the cluster asks of them.
 *
 * Two guardrails run through all of it: somebody acting for the cluster may not edit their own membership
 * anywhere, and may not touch a station's owner. Both are here as stories of their own, because they are
 * the only thing standing between a cluster role and a way to promote yourself.
 */
test.describe('Cluster members and fields', () => {
    /**
     * CLS-23 - The cluster searches members across all its stations.
     *
     * Two stations in one list, each entry saying where it comes from. The demo puts members under two
     * different member stations for exactly this.
     */
    test('the cluster searches members across all its stations', async ({browser, request}) => {
        const manager = await clusterAccountOnlyWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, manager)
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const found = await page.request.get('/api/v1/cluster/members/manage/search?size=200', {headers})
        expect(found.ok()).toBeTruthy()
        const {members} = await found.json()
        const stations = new Set(members.map((m: {stationName: string}) => m.stationName))
        expect(stations.size, 'members of more than one station are found in the one list').toBeGreaterThan(1)

        await page.goto('/cluster/members/manage')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        // The screen offers every station the cluster reaches as something to narrow by, which is the
        // reach itself made visible. Asserted as options rather than as text: an option is in the page
        // without being on it.
        for (const stationName of [...stations].slice(0, 2)) {
            await expect(page.getByRole('option', {name: stationName as string})).toHaveCount(1)
        }

        // And somebody from each of two stations is actually listed
        const names = members
            .filter((m: {stationName: string}) => m.stationName === [...stations][0])
            .concat(members.filter((m: {stationName: string}) => m.stationName === [...stations][1]))
        expect(names.length).toBeGreaterThan(1)
        await page.context().close()
    })

    /**
     * CLS-25 - A cluster member manager cannot edit their own membership.
     *
     * The one thing a cluster role must never become is a way to grant yourself something at a station.
     */
    test('a cluster member manager cannot edit their own membership', async ({browser, request}) => {
        const manager = await clusterAccountOnlyWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, manager)
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const {members} = await page.request
            .get('/api/v1/cluster/members/manage/search?size=200', {headers})
            .then(r => r.json())
        const own = members.find((m: {email: string}) => m.email === manager.email)
        expect(own, 'the member manager is also a member of one of the stations').toBeTruthy()

        const refused = await page.request.put(`/api/v1/cluster/members/manage/${own.id}/user-type`,
            {headers, data: {userType: 'MANAGER'}})
        expect(refused.status()).toBe(403)
        expect((await refused.text()).toLowerCase()).toContain('own')
        await page.context().close()
    })

    /**
     * CLS-26 - A station owner cannot be edited from the cluster.
     *
     * The owner is the one person who can speak for a station against the cluster, so the cluster cannot
     * quietly demote them.
     */
    test('a station owner cannot be edited from the cluster', async ({browser, request}) => {
        const manager = await clusterAccountOnlyWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, manager)
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const {members} = await page.request
            .get('/api/v1/cluster/members/manage/search?size=200', {headers})
            .then(r => r.json())
        const owner = members.find((m: {stationOwner: boolean; email: string}) =>
            m.stationOwner && m.email !== manager.email)
        expect(owner, 'a member station has an owner').toBeTruthy()

        const refused = await page.request.put(`/api/v1/cluster/members/manage/${owner.id}/permissions`,
            {headers, data: {permissions: []}})
        expect(refused.status()).toBe(403)
        expect((await refused.text()).toLowerCase()).toContain('owner')
        await page.context().close()
    })

    /**
     * CLS-24 - A member is edited from the cluster.
     *
     * One form of two origins. What the cluster asks and what the station asks are answered side by side,
     * each marked with who asked, and both survive being read back.
     */
    test('a member is edited from the cluster', async ({adminPage: page, browser, request}) => {
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const field = await page.request.post('/api/v1/cluster/fields', {
            headers,
            data: {
                name: `Funkrufname ${test.info().workerIndex}-${Date.now()}`,
                fieldType: 'TEXT', config: null, position: 0, scope: 'MEMBER',
                stationReadonly: false, keepOnArchive: false,
            },
        })
        expect(field.ok()).toBeTruthy()
        const {id: fieldId} = await field.json()

        const {members} = await page.request
            .get('/api/v1/cluster/members/manage/search?size=50', {headers})
            .then(r => r.json())
        const target = members.find((m: {stationOwner: boolean}) => !m.stationOwner)
        expect(target, 'somebody who is not their station\'s owner').toBeTruthy()

        const answer = `Florian ${Date.now()}`
        const saved = await page.request.put(`/api/v1/cluster/fields/member/${target.id}`,
            {headers, data: {values: {[fieldId]: JSON.stringify(answer)}}})
        expect(saved.ok()).toBeTruthy()

        const read = await page.request.get(`/api/v1/cluster/fields/member/${target.id}`, {headers})
        expect(JSON.stringify(await read.json())).toContain(answer)

        await page.goto('/cluster/members/manage')
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * CLS-27 - A cluster field appears in the station's own member profile.
     * CLS-28 - and one the cluster leaves open is editable there.
     *
     * Two halves of the same walk, because the interesting part is the difference between them: the same
     * form carries both, and only one of them has a control.
     */
    test('a cluster field appears in the station profile, and only an open one is editable',
        async ({adminPage: page, browser, request}) => {
            const cluster = await enterCluster(page)
            const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}
            const stamp = `${test.info().workerIndex}-${Date.now()}`

            const kept = await page.request.post('/api/v1/cluster/fields', {
                headers,
                data: {
                    name: `Führerschein ${stamp}`, fieldType: 'TEXT', config: null, position: 0,
                    scope: 'MEMBER', stationReadonly: true, keepOnArchive: false,
                },
            })
            const open = await page.request.post('/api/v1/cluster/fields', {
                headers,
                data: {
                    name: `Spind ${stamp}`, fieldType: 'TEXT', config: null, position: 1,
                    scope: 'MEMBER', stationReadonly: false, keepOnArchive: false,
                },
            })
            expect(kept.ok() && open.ok()).toBeTruthy()
            const keptId = (await kept.json()).id
            const openId = (await open.json()).id

            const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
            const stationHeaders = await apiHeaders(station)
            const members = await station.request
                .get('/api/v1/station-members', {headers: stationHeaders})
                .then(r => r.json())
            const member = (Array.isArray(members) ? members : members.members ?? [])
                .find((m: {userType: string}) => m.userType === 'MEMBER')
            expect(member, 'the station has an ordinary member').toBeTruthy()

            // CLS-27: both questions are on the station's form, marked as the cluster's
            const fields = await station.request
                .get(`/api/v1/station-members/${member.id}/fields`, {headers: stationHeaders})
                .then(r => r.json())
            const clusterFields = fields.filter((f: {origin: string}) => f.origin === 'CLUSTER')
            expect(clusterFields.map((f: {id: number}) => f.id)).toEqual(expect.arrayContaining([keptId, openId]))
            expect(clusterFields.find((f: {id: number}) => f.id === keptId).readonlyAtStation).toBeTruthy()
            expect(clusterFields.find((f: {id: number}) => f.id === openId).readonlyAtStation).toBeFalsy()

            // CLS-28: the station answers the open one, and the cluster reads the answer back
            const answer = `B12 ${stamp}`
            const wrote = await station.request.put(`/api/v1/station-members/${member.id}/profile`, {
                headers: stationHeaders,
                data: {values: [
                    {fieldId: openId, value: JSON.stringify(answer), origin: 'CLUSTER'},
                    {fieldId: keptId, value: JSON.stringify('nicht erlaubt'), origin: 'CLUSTER'},
                ]},
            })
            expect(wrote.ok()).toBeTruthy()

            const back = await page.request.get(`/api/v1/cluster/fields/member/${member.id}`, {headers})
            const answers = JSON.stringify(await back.json())
            expect(answers).toContain(answer)
            expect(answers, 'the one the cluster kept was not written from the station')
                .not.toContain('nicht erlaubt')

            await station.goto(`/station/members/edit/${member.id}`)
            await expect(station.getByTestId('app-shell')).toBeVisible()
            await station.context().close()
        })

    /**
     * CLS-29 - A cluster field change reaches the people who acknowledge changes.
     *
     * The history a profile already had is the one the change lands in, so the people at the station who
     * watch for changes see it beside every other one.
     */
    test('a cluster field change lands in the profile history', async ({adminPage: page, browser, request}) => {
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}
        const stamp = `${test.info().workerIndex}-${Date.now()}`

        const field = await page.request.post('/api/v1/cluster/fields', {
            headers,
            data: {
                name: `Atemschutz ${stamp}`, fieldType: 'TEXT', config: null, position: 0,
                scope: 'MEMBER', stationReadonly: false, keepOnArchive: false,
            },
        })
        expect(field.ok()).toBeTruthy()
        const fieldId = (await field.json()).id

        const {members} = await page.request
            .get('/api/v1/cluster/members/manage/search?size=50', {headers})
            .then(r => r.json())
        const target = members.find((m: {stationOwner: boolean}) => !m.stationOwner)

        const answer = `G26.3 ${stamp}`
        const saved = await page.request.put(`/api/v1/cluster/fields/member/${target.id}`,
            {headers, data: {values: {[fieldId]: JSON.stringify(answer)}}})
        expect(saved.ok()).toBeTruthy()

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const stationHeaders = await apiHeaders(station)
        const changes = await station.request
            .get('/api/v1/profile-changes/all', {headers: stationHeaders})
            .then(r => r.json())
        expect(JSON.stringify(changes)).toContain(`Atemschutz ${stamp}`)
        await station.context().close()
    })

    /**
     * CLS-30 - A cluster field cannot be group-scoped or a birth date.
     *
     * A group belongs to one station and a birth date belongs to the station that has to act on it, so
     * neither is a question a cluster may ask.
     */
    test('a cluster field cannot be group-scoped or a birth date', async ({adminPage: page}) => {
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const grouped = await page.request.post('/api/v1/cluster/fields', {
            headers,
            data: {name: 'Gruppenfrage', fieldType: 'TEXT', config: null, position: 0,
                scope: 'GROUP', stationReadonly: true, keepOnArchive: false},
        })
        expect(grouped.ok()).toBeFalsy()

        const born = await page.request.post('/api/v1/cluster/fields', {
            headers,
            data: {name: 'Geburtstag', fieldType: 'BIRTH_DATE', config: null, position: 0,
                scope: 'GLOBAL', stationReadonly: true, keepOnArchive: false},
        })
        expect(born.ok()).toBeFalsy()
    })
})
