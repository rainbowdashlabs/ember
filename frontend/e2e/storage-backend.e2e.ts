/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import {ownCluster} from './fixtures/cluster'
import {
    movedCounts,
    putSomething,
    readBack,
    s3Target,
    sftpTarget,
    smbTarget,
    unreachableTarget,
} from './fixtures/storage'

/**
 * A station pointing its own files at storage of its own, and bringing them home again.
 *
 * Walked by nothing until now, which is uncomfortable for the one act in the application that copies every
 * byte a station owns and then deletes the originals. The stack runs an SFTP server, an S3 service and a
 * Samba share for exactly this.
 *
 * Three rules these stories follow, because four workers share one stack:
 *
 * <ul>
 *   <li>never the seeded station: each story builds its own, so the per-station migration lock is never
 *       contended and no other story's files are carried anywhere,
 *   <li>never the instance default: swapping that moves every station there is and holds a read-only flag
 *       while it does, which would fail the other three workers outright,
 *   <li>assert on the bytes: a screen saying "on your own storage" says exactly the same thing after a
 *       migration that copied nothing.
 * </ul>
 */
test.describe('Station storage backend', () => {
    /**
     * The whole act: a file goes up, the station points itself at the SFTP service, and the file is still
     * the file afterwards. The counts are what say the bytes actually travelled.
     */
    test('a station moves its files to storage of its own', async ({adminPage, browser, request}) => {
        const own = await ownCluster(adminPage, browser, request, 'Speicher')
        const headers = await apiHeaders(own.stationPage)
        const file = await putSomething(own.stationPage.request, headers, 'umzug')

        const applied = await own.stationPage.request.post('/api/v1/station/storage/backend/apply', {
            headers,
            data: sftpTarget(),
        })
        expect(applied.ok(), `the apply went through (${await applied.text()})`).toBeTruthy()

        const counts = movedCounts(await applied.json())
        expect(counts.copied, 'something was actually carried across').toBeGreaterThan(0)
        expect(counts.deleted, 'and the originals were cleaned up behind it').toBe(counts.copied)
        expect(await readBack(own.stationPage.request, headers, file)).toBe(file.bytes)

        await own.stationPage.context().close()
    })

    /**
     * And back again, which is the same machinery in the other direction. The trail is what proves both
     * halves happened rather than one.
     */
    test('a station comes back to the storage of the instance', async ({adminPage, browser, request}) => {
        const own = await ownCluster(adminPage, browser, request, 'Rueckweg')
        const headers = await apiHeaders(own.stationPage)
        const file = await putSomething(own.stationPage.request, headers, 'rueckweg')

        await own.stationPage.request.post('/api/v1/station/storage/backend/apply', {
            headers,
            data: sftpTarget(),
        })
        const home = await own.stationPage.request.post('/api/v1/station/storage/backend/apply', {
            headers,
            data: {type: 'LOCAL'},
        })
        expect(home.ok(), `coming home went through (${await home.text()})`).toBeTruthy()
        expect(movedCounts(await home.json()).copied, 'the files came back').toBeGreaterThan(0)
        expect(await readBack(own.stationPage.request, headers, file)).toBe(file.bytes)

        const audit = await own.stationPage.request
            .get('/api/v1/station/storage/audit', {headers})
            .then(r => r.json())
        const completed = audit.filter((entry: {action: string}) => entry.action === 'MIGRATION_COMPLETED')
        expect(completed.length, 'a finished migration in each direction').toBeGreaterThanOrEqual(2)

        await own.stationPage.context().close()
    })

    /**
     * An apply is all or nothing. A target nothing answers on is refused before a byte moves, and the
     * station is still where it was with its file still readable.
     */
    test('a target that cannot be reached moves nothing', async ({adminPage, browser, request}) => {
        const own = await ownCluster(adminPage, browser, request, 'Unerreichbar')
        const headers = await apiHeaders(own.stationPage)
        const file = await putSomething(own.stationPage.request, headers, 'unerreichbar')

        const refused = await own.stationPage.request.post('/api/v1/station/storage/backend/apply', {
            headers,
            data: unreachableTarget(),
        })
        expect(refused.ok(), 'the apply is refused rather than half done').toBeFalsy()

        const backend = await own.stationPage.request
            .get('/api/v1/station/storage/backend', {headers})
            .then(r => r.json())
        expect(backend.override, 'and the station is still where it was').toBeNull()
        expect(await readBack(own.stationPage.request, headers, file)).toBe(file.bytes)

        await own.stationPage.context().close()
    })

    /**
     * All three variants answer, through the connection test rather than through three applies: what
     * differs between them is the credentials and the probe, and three moves would treble the runtime to
     * say the same thing once.
     */
    test('all three kinds of storage answer', async ({adminPage, browser, request}) => {
        const own = await ownCluster(adminPage, browser, request, 'Varianten')
        const headers = await apiHeaders(own.stationPage)

        for (const target of [sftpTarget(), s3Target(), smbTarget()]) {
            const probe = await own.stationPage.request.post('/api/v1/station/storage/backend/probe-config', {
                headers,
                data: target,
            })
            expect(probe.ok()).toBeTruthy()
            const result = await probe.json()
            expect(result.healthy, `${target.type} answered: ${result.error ?? ''}`).toBeTruthy()
        }

        await own.stationPage.context().close()
    })
})
