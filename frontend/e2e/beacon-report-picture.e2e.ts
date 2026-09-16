/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, peerInternalUrl, test} from './fixtures/peer'
import type {APIRequestContext} from '@playwright/test'

/**
 * A report with a picture, forwarded from one installation to another.
 *
 * <p>Everything else about the picture is walked on one instance: taken or attached, covered, kept,
 * shown. What only two instances can show is the part that broke in the wild, which is the picture
 * making the journey: it is a delivery of its own, sent before the report that names it, and the
 * report does not go at all where the picture did not arrive. A story on one instance would have
 * said nothing about any of that.
 */

/** A small red PNG, so a real picture is attached rather than a string pretending to be one. */
const PICTURE = 'iVBORw0KGgoAAAANSUhEUgAAAPAAAAB4CAIAAABD1OhwAAABW0lEQVR4nO3SQQkAMAzAwAqrfyZrJgaDcHAC8'
    + 'sicXciY7wXwkKFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiq'
    + 'FJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQ'
    + 'YmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxN'
    + 'iqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0'
    + 'KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk3IBEKuoNezOBt0AAAAASUVORK5CYII='

/**
 * Points an instance at a beacon, or makes it one.
 *
 * <p>Every switch is written, none merged over what happens to be stored: these settings are one
 * row for the whole instance, and a story that wrote only the switches it cared about would carry
 * the rest from whenever it happened to read them.
 */
async function beaconSettings(api: APIRequestContext, settings: Record<string, unknown>) {
    const written = await api.put('/api/v1/admin/beacon', {
        data: {
            enabled: false,
            url: '',
            forwardProblems: false,
            forwardReports: false,
            reviewReportPictures: true,
            metricsEnabled: false,
            receiving: false,
            contactName: '',
            contactMail: '',
            ...settings,
        },
    })
    expect(written.ok(), 'the beacon settings were written').toBeTruthy()
    return written.json()
}

/** What a beacon has been sent, newest first. */
async function collectedReports(api: APIRequestContext) {
    const answer = await api.get('/api/v1/admin/beacon/collected/reports?includeAcknowledged=true')
    expect(answer.ok(), 'the beacon listed what it holds').toBeTruthy()
    return answer.json() as Promise<{id: number; message: string; screenshotFileId?: number | null}[]>
}

/**
 * Both stories write the one row of beacon settings an instance has, so they take their turn rather
 * than overwriting each other's answer to whether pictures are looked at before they go.
 */
test.describe('A report with a picture reaches a beacon', () => {
    test.describe.configure({mode: 'serial'})

    /**
     * Sent as it is written rather than held for somebody to look at, which is the path a report
     * with a picture takes where an operator has said they would rather not be asked each time.
     */
    test('the picture travels with the report and the beacon keeps it', async ({
        homeManagerApi,
        homeAdminApi,
        peerAdminApi,
    }) => {
        await beaconSettings(peerAdminApi, {enabled: true, receiving: true})
        await beaconSettings(homeAdminApi, {
            enabled: true,
            url: peerInternalUrl(),
            forwardReports: true,
            reviewReportPictures: false,
        })

        const said = `Mit Bild an den Beacon ${Date.now()}`
        const written = await homeManagerApi.post('/api/v1/problem-reports', {
            data: {
                message: said,
                pageUrl: 'http://localhost/station/dashboard/overview',
                screenshot: PICTURE,
            },
        })
        expect(written.status(), 'the report was written').toBe(201)
        expect((await written.json()).screenshotFileId, 'it kept the picture').toBeTruthy()

        await expect
            .poll(async () => (await collectedReports(peerAdminApi)).find(r => r.message.includes(said)), {
                timeout: 20_000,
                message: 'the beacon never received the report',
            })
            .toBeTruthy()

        const arrived = (await collectedReports(peerAdminApi)).find(r => r.message.includes(said))
        expect(arrived?.screenshotFileId, 'the report arrived with its picture').toBeTruthy()
    })

    /** A report with no picture is the ordinary case and must not be held up by any of this. */
    test('a report without a picture still reaches the beacon', async ({
        homeManagerApi,
        homeAdminApi,
        peerAdminApi,
    }) => {
        await beaconSettings(peerAdminApi, {enabled: true, receiving: true})
        await beaconSettings(homeAdminApi, {enabled: true, url: peerInternalUrl(), forwardReports: true})

        const said = `Ohne Bild an den Beacon ${Date.now()}`
        const written = await homeManagerApi.post('/api/v1/problem-reports', {
            data: {message: said, pageUrl: 'http://localhost/station/dashboard/overview'},
        })
        expect(written.status()).toBe(201)

        await expect
            .poll(async () => (await collectedReports(peerAdminApi)).find(r => r.message.includes(said)), {
                timeout: 20_000,
                message: 'the beacon never received the report',
            })
            .toBeTruthy()
    })
})
