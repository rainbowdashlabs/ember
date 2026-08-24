/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {ownCluster, stationUnder, type OwnCluster} from './fixtures/cluster'
import {putSomething, readBack, sftpTarget, unreachableTarget} from './fixtures/storage'

/**
 * An association keeping its files on storage of its own, and deciding whether its stations do too.
 *
 * Two facts run through every story here and they are not the same fact: what the association decided, which
 * is written the moment somebody presses save, and where a station's bytes actually are, which changes only
 * when a copy finishes. A station where those two differ is out of place, and moving it is an act somebody
 * performs one station at a time.
 *
 * Every story builds an association of its own, for the same reason the room stories do: this reaches every
 * member station at once, and doing it on the seeded association would carry files four other workers are
 * reading at that moment.
 */
test.describe('Cluster storage backend', () => {
    /** The association's backend screen, entered the way the switcher enters it. */
    async function backendScreen(page: Page, own: OwnCluster) {
        await page.goto('/cluster/storage/backend')
        await page.evaluate(uid => window.localStorage.setItem('cluster_id', uid), own.uid)
        await page.goto('/cluster/storage/backend')
        await expect(page.getByTestId('app-shell')).toBeVisible()
    }

    /** Saves the association's storage and says what it is for, which is two acts and not one. */
    async function pointAt(page: Page, own: OwnCluster, reach: string, locked = false) {
        const saved = await page.request.post('/api/v1/cluster/storage/backend/apply',
            {headers: own.headers, data: sftpTarget()})
        expect(saved.ok(), `the association saved its storage (${await saved.text()})`).toBeTruthy()
        const decided = await page.request.put('/api/v1/cluster/storage/backend/policy',
            {headers: own.headers, data: {reach, locked}})
        expect(decided.ok(), `the association decided what it is for (${await decided.text()})`).toBeTruthy()
    }

    /** One row of the placement list, by the station it is about. */
    async function placementFor(page: Page, own: OwnCluster, stationUid: string) {
        const rows = await page.request
            .get('/api/v1/cluster/storage/backend/placements', {headers: own.headers})
            .then(r => r.json())
        return rows.find((row: {stationUid: string}) => row.stationUid === stationUid)
    }

    /** Carries one station across, as the association. */
    async function move(page: Page, own: OwnCluster, stationUid: string) {
        return page.request.post(`/api/v1/cluster/storage/backend/placements/${stationUid}/move`,
            {headers: own.headers})
    }

    /**
     * CLS-82 - The association keeps its own files on its own disk.
     *
     * Its own store is a station like any other in the placement list, and it is the one thing "a backend for
     * the association itself" has to mean. It never happened before this work: the lookup went through the
     * membership row its own station does not carry.
     */
    test('the association keeps its own files on its own disk', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'EigenerSpeicher')
        const article = await putSomething(page.request, own.contentHeaders, 'verbandsdatei')

        await pointAt(page, own, 'OWN_FILES')
        const rows = await page.request
            .get('/api/v1/cluster/storage/backend/placements', {headers: own.headers})
            .then(r => r.json())
        const home = rows.find((row: {homeStation: boolean}) => row.homeStation)
        expect(home, 'the association owns a store and it is in the list').toBeTruthy()
        expect(home.inPlace, 'and deciding did not carry it anywhere').toBeFalsy()

        const moved = await move(page, own, home.stationUid)
        expect(moved.ok(), `the association's own files moved (${await moved.text()})`).toBeTruthy()

        expect((await placementFor(page, own, home.stationUid)).inPlace).toBeTruthy()
        expect(await readBack(page.request, own.contentHeaders, article)).toBe(article.bytes)
    })

    /**
     * CLS-83 - The association takes its stations' storage over.
     *
     * One of two stations is moved and the other is not, which is the whole of "on demand": deciding does not
     * carry anything, and carrying one station does not carry its neighbour.
     */
    test('the association takes its stations storage over one at a time',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'Uebernahme')
            const second = await stationUnder(page, browser, request, own.headers, `Zweite ${Date.now()}`)
            const stationHeaders = await apiHeaders(own.stationPage)
            const file = await putSomething(own.stationPage.request, stationHeaders, 'uebernahme')

            await pointAt(page, own, 'EVERY_STATION', true)

            expect((await placementFor(page, own, own.stationUid)).inPlace).toBeFalsy()
            expect((await placementFor(page, own, second.uid)).inPlace).toBeFalsy()

            const moved = await move(page, own, own.stationUid)
            expect(moved.ok(), `the first station moved (${await moved.text()})`).toBeTruthy()
            expect((await moved.json()).copied, 'and something was actually carried').toBeGreaterThan(0)

            expect((await placementFor(page, own, own.stationUid)).inPlace).toBeTruthy()
            expect(
                (await placementFor(page, own, second.uid)).inPlace,
                'and the one nobody moved is still where it was',
            ).toBeFalsy()
            expect(await readBack(own.stationPage.request, stationHeaders, file)).toBe(file.bytes)

            await second.page.context().close()
            await own.stationPage.context().close()
        })

    /**
     * CLS-84 - A station may opt out while the association allows it.
     *
     * The story ends on the room screen rather than this one on purpose: a station paying for its own storage
     * is bounded by nobody, and that is the consequence an association is choosing between when it decides
     * whether to lock.
     */
    test('a station may bring its own while the association allows it',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'Abwahl')
            const stationHeaders = await apiHeaders(own.stationPage)
            await pointAt(page, own, 'EVERY_STATION', false)

            const brought = await own.stationPage.request.post('/api/v1/station/storage/backend/apply',
                {headers: stationHeaders, data: sftpTarget()})
            expect(brought.ok(), `the station brought its own (${await brought.text()})`).toBeTruthy()

            const row = await placementFor(page, own, own.stationUid)
            expect(row.actual).toBe('ITS_OWN')
            expect(row.inPlace, 'which is a legal opt-out rather than a station out of place').toBeTruthy()

            const quotas = await own.stationPage.request
                .get('/api/v1/storage/usage', {headers: stationHeaders})
                .then(r => r.json())
            expect(quotas.usesOwnBackend, 'and whoever pays sets the limit, which is nobody now').toBeTruthy()

            await own.stationPage.context().close()
        })

    /**
     * CLS-85 - A locked station changes nothing, and a disabled button is not a permission.
     */
    test('a locked station changes nothing', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Gesperrt')
        const stationHeaders = await apiHeaders(own.stationPage)
        await pointAt(page, own, 'EVERY_STATION', true)

        const answer = await own.stationPage.request
            .get('/api/v1/station/storage/backend', {headers: stationHeaders})
            .then(r => r.json())
        expect(answer.locked, 'the station is told who decides').toBeTruthy()
        expect(answer.clusterName).toBe(own.name)

        const refused = await own.stationPage.request.post('/api/v1/station/storage/backend/apply',
            {headers: stationHeaders, data: sftpTarget()})
        expect(refused.ok(), 'and the endpoint refuses the same act posted directly').toBeFalsy()

        await own.stationPage.request.get('/station/manage/storage/backend')
        await own.stationPage.goto('/station/manage/storage/backend')
        await expect(own.stationPage.getByTestId('station-storage-locked')).toBeVisible()

        await own.stationPage.context().close()
    })

    /**
     * CLS-86 - The association drops its disk and everybody comes home.
     *
     * What people stand on is kept rather than deleted, because the alternative is a station pointed at
     * nothing, and the lock does not hold anybody onto storage that is gone.
     */
    test('the association drops its disk and everybody comes home',
        async ({adminPage: page, browser, request}) => {
            const own = await ownCluster(page, browser, request, 'Aufgabe')
            const stationHeaders = await apiHeaders(own.stationPage)
            const file = await putSomething(own.stationPage.request, stationHeaders, 'aufgabe')
            await pointAt(page, own, 'EVERY_STATION', true)
            expect((await move(page, own, own.stationUid)).ok()).toBeTruthy()

            const dropped = await page.request.delete('/api/v1/cluster/storage/backend', {headers: own.headers})
            expect(dropped.ok(), `the association gave its storage up (${await dropped.text()})`).toBeTruthy()

            const row = await placementFor(page, own, own.stationUid)
            expect(row.actual, 'the files are still where they were carried').toBe('THE_CLUSTERS')
            expect(row.inPlace, 'and out of place from this moment').toBeFalsy()

            const home = await move(page, own, own.stationUid)
            expect(home.ok(), `and the station comes home (${await home.text()})`).toBeTruthy()
            expect(await readBack(own.stationPage.request, stationHeaders, file)).toBe(file.bytes)

            await own.stationPage.context().close()
        })

    /**
     * CLS-87 and CLS-88 - A joining station arrives with its files and a released one takes them with it.
     *
     * One story rather than two, because it is one station walking in and out again, and the point in both
     * directions is the same: the copy finishes before the membership is written.
     */
    test('a station arrives with its files and leaves with them', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Beitritt')
        await pointAt(page, own, 'EVERY_STATION', true)

        const joining = await stationUnder(page, browser, request, own.headers, `Beitretende ${Date.now()}`)
        const joiningHeaders = await apiHeaders(joining.page)
        const file = await putSomething(joining.page.request, joiningHeaders, 'beitritt')

        // Out and back in again, which is the only way to walk a join on a station that has files already
        const released = await page.request.delete(`/api/v1/cluster/stations/${joining.uid}`,
            {headers: own.headers})
        expect(released.ok(), `the station was let go (${await released.text()})`).toBeTruthy()
        expect(await readBack(joining.page.request, joiningHeaders, file)).toBe(file.bytes)

        const applied = await joining.page.request.post('/api/v1/station/cluster/applications',
            {headers: joiningHeaders, data: {clusterUid: own.uid}})
        expect(applied.ok(), `and asked to come back (${await applied.text()})`).toBeTruthy()
        const waiting = await page.request
            .get('/api/v1/cluster/applications', {headers: own.headers})
            .then(r => r.json())
        const accepted = await page.request.put(`/api/v1/cluster/applications/${waiting[0].id}`,
            {headers: own.headers, data: {approve: true, reason: null}})
        expect(accepted.ok(), `the association took it back in (${await accepted.text()})`).toBeTruthy()

        expect(
            (await placementFor(page, own, joining.uid)).actual,
            'and it arrived on the association storage rather than being pointed at it',
        ).toBe('THE_CLUSTERS')
        expect(await readBack(joining.page.request, joiningHeaders, file)).toBe(file.bytes)

        await joining.page.context().close()
        await own.stationPage.context().close()
    })

    /**
     * CLS-89 - A copy that cannot run refuses the act rather than half doing it.
     *
     * The promise of moving first and acting second, which would otherwise be believed rather than known.
     */
    test('a copy that cannot run refuses the act', async ({adminPage: page, browser, request}) => {
        const own = await ownCluster(page, browser, request, 'Unerreichbar')

        // The station is built before the association points anywhere: making one under a cluster whose
        // storage cannot be reached is itself refused, which is this story's subject one step earlier
        const joining = await stationUnder(page, browser, request, own.headers, `Ohne Ziel ${Date.now()}`)
        const joiningHeaders = await apiHeaders(joining.page)
        await page.request.delete(`/api/v1/cluster/stations/${joining.uid}`, {headers: own.headers})

        const saved = await page.request.post('/api/v1/cluster/storage/backend/apply',
            {headers: own.headers, data: unreachableTarget()})
        expect(saved.ok()).toBeTruthy()
        const decided = await page.request.put('/api/v1/cluster/storage/backend/policy',
            {headers: own.headers, data: {reach: 'EVERY_STATION', locked: true}})
        expect(decided.ok()).toBeTruthy()

        await joining.page.request.post('/api/v1/station/cluster/applications',
            {headers: joiningHeaders, data: {clusterUid: own.uid}})
        const waiting = await page.request
            .get('/api/v1/cluster/applications', {headers: own.headers})
            .then(r => r.json())
        const refused = await page.request.put(`/api/v1/cluster/applications/${waiting[0].id}`,
            {headers: own.headers, data: {approve: true, reason: null}})
        expect(refused.ok(), 'the station stays unjoined rather than joined and stranded').toBeFalsy()

        const station = await joining.page.request
            .get('/api/v1/station/cluster', {headers: joiningHeaders})
            .then(r => r.json())
        expect(station.clusterUid ?? null, 'and it answers to nobody').toBeNull()

        await joining.page.context().close()
        await own.stationPage.context().close()
    })
})
