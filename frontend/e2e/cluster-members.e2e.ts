/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, enterCluster, clusterAccountOnlyWith, clusterPage,
    clusterStationManager, pageAsThrowaway} from './fixtures/auth'
import type {Page} from '@playwright/test'

/**
 * The people at every station under a cluster, and the questions the cluster asks of them.
 *
 * Two guardrails run through all of it: somebody acting for the cluster may not edit their own membership
 * anywhere, and may not touch a station's owner. Both are here as stories of their own, because they are
 * the only thing standing between a cluster role and a way to promote yourself.
 */
/**
 * Which membership the signed-in page is, asked of the application.
 *
 * <p>Their own row is picked out by id rather than by address: an address is what several stories
 * rewrite, and a row found by one is a row that may have moved on.
 */
async function ownMembershipId(page: Page): Promise<number> {
    const session = await page.request.get('/api/v1/session', {headers: await apiHeaders(page)})
    expect(session.ok(), `the signed-in page has a session (${await session.text()})`).toBeTruthy()
    return (await session.json()).member.id
}

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
        const mine = await ownMembershipId(page)
        const own = members.find((m: {id: number}) => m.id === mine)
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
        const mine = await ownMembershipId(page)
        const owner = members.find((m: {stationOwner: boolean; id: number}) =>
            m.stationOwner && m.id !== mine)
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
                fieldType: 'TEXT', config: null,
                stationReadonly: false, keepOnArchive: false,
            },
        })
        expect(field.ok()).toBeTruthy()
        const {id: fieldId} = await field.json()
        await page.request.put(`/api/v1/cluster/fields/${fieldId}/assignments`, {
            headers,
            data: {role: 'MEMBER', position: 0},
        })

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

        // Stored is half of it. The other half is that somebody at the cluster can open that person
        // and read what was answered, which is the screen this story is named after and which for a
        // long time did not exist at all.
        await page.goto(`/cluster/members/${target.id}`)
        await expect(page.getByTestId('app-shell')).toBeVisible()
        // Read off the inputs rather than matched as an attribute: the form sets the value as a
        // property, so `input[value=...]` would look at what the markup said and not at what is there.
        await expect.poll(
            () => page.getByRole('textbox')
                .evaluateAll((inputs, want) =>
                    inputs.some(input => (input as HTMLInputElement).value === want), answer),
            {message: 'the answer is on the person\'s own screen, not merely in the database'},
        ).toBe(true)
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
                    name: `Führerschein ${stamp}`, fieldType: 'TEXT', config: null,
                    stationReadonly: true, keepOnArchive: false,
                },
            })
            const open = await page.request.post('/api/v1/cluster/fields', {
                headers,
                data: {
                    name: `Spind ${stamp}`, fieldType: 'TEXT', config: null,
                    stationReadonly: false, keepOnArchive: false,
                },
            })
            expect(kept.ok() && open.ok()).toBeTruthy()
            const keptId = (await kept.json()).id
            const openId = (await open.json()).id
            for (const [position, id] of [keptId, openId].entries()) {
                await page.request.put(`/api/v1/cluster/fields/${id}/assignments`, {
                    headers,
                    data: {role: 'MEMBER', position},
                })
            }

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
                name: `Atemschutz ${stamp}`, fieldType: 'TEXT', config: null,
                stationReadonly: false, keepOnArchive: false,
            },
        })
        expect(field.ok()).toBeTruthy()
        const fieldId = (await field.json()).id
        await page.request.put(`/api/v1/cluster/fields/${fieldId}/assignments`, {
            headers,
            data: {role: 'MEMBER', position: 0},
        })

        // Answered for somebody at the station that will read the history. A station's history is its
        // own people, so answering for whoever came first across all the stations reads back as nothing
        // the moment another story takes somebody on somewhere else.
        const manager = await clusterStationManager(request)
        const {members} = await page.request
            .get('/api/v1/cluster/members/manage/search?size=50', {headers})
            .then(r => r.json())
        const target = members.find((m: {stationOwner: boolean; stationUid: string}) =>
            !m.stationOwner && m.stationUid === manager.stationId)
        expect(target, 'the reading station has somebody to answer for').toBeTruthy()

        const answer = `G26.3 ${stamp}`
        const saved = await page.request.put(`/api/v1/cluster/fields/member/${target.id}`,
            {headers, data: {values: {[fieldId]: JSON.stringify(answer)}}})
        expect(saved.ok()).toBeTruthy()

        const station = await pageAsThrowaway(browser, request, [], manager)
        const stationHeaders = await apiHeaders(station)
        // Asked for more than one page: several stories write answers at once, and the newest twenty
        // is not a promise that the one just written is among them
        const changes = await station.request
            .get('/api/v1/profile-changes/all?limit=200', {headers: stationHeaders})
            .then(r => r.json())
        expect(JSON.stringify(changes)).toContain(`Atemschutz ${stamp}`)
        await station.context().close()
    })

    /**
     * CLS-30 - A cluster field cannot be a birth date.
     *
     * A birth date belongs to the station that has to act on it, so it is not a question a cluster may
     * ask. A member group is no longer refusable at all: a group is a target an assignment names, and a
     * cluster's assignments name a kind of member and nothing else, so there is nothing to refuse.
     */
    test('a cluster field cannot be a birth date', async ({adminPage: page}) => {
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const born = await page.request.post('/api/v1/cluster/fields', {
            headers,
            data: {name: 'Geburtstag', fieldType: 'BIRTH_DATE', config: null,
                stationReadonly: true, keepOnArchive: false},
        })
        expect(born.ok()).toBeFalsy()
    })
})
