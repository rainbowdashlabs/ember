/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * The same station, inside an association and outside it.
 *
 * The demo builds one station twice from the same seeders and the same data: one of the two answers to
 * the demo association and the other answers to nobody. Every feature has two answers, and these are the
 * stories that hold the two up against each other. Whatever they find is the association's doing,
 * because nothing else about the two stations differs on purpose.
 *
 * Each of them reads the same thing at both stations rather than asserting a number at one. A story that
 * only ever looked at the station inside an association could never say what the feature does without
 * one, which is the half the demo could not show before.
 */

/** What one station holds, however it came by it. */
async function heldGear(page: Page): Promise<{ownerKind: string; ownerClusterId: number | null}[]> {
    const items = await page.request
        .get('/api/v1/inventories/all-items', {headers: await apiHeaders(page)})
        .then(r => r.json())
    return Array.isArray(items) ? items : items.items ?? []
}

/** How much room a station has, as its own storage screen reads it. */
async function roomFor(page: Page): Promise<number> {
    const usage = await page.request
        .get('/api/v1/storage/usage', {headers: await apiHeaders(page)})
        .then(r => r.json())
    return usage.quotaBytes
}

/** The questions asked of an ordinary member of this station, and who asks each of them. */
async function questionsAbout(page: Page): Promise<{name: string; origin: string}[]> {
    const headers = await apiHeaders(page)
    const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
    const member = (Array.isArray(members) ? members : members.members ?? [])
        .find((m: {userType: string}) => m.userType === 'MEMBER')
    expect(member, 'the station has an ordinary member').toBeTruthy()

    return page.request
        .get(`/api/v1/station-members/${member.id}/fields`, {headers})
        .then(r => r.json())
}

test.describe('A station inside an association and one outside it', () => {
    /**
     * Gear a station holds but does not own is the same record either way. What differs is whether the
     * owner can be asked: the association adopted its station's gear on the way in and can answer for it,
     * while a station standing alone records an owner nobody on this instance can reach.
     */
    test('gear the station does not own names an association at one and nobody at the other', async ({
        clusterStationManagerPage: inside, standaloneStationManagerPage: outside,
    }) => {
        const held = await heldGear(inside)
        expect(held.some(item => item.ownerKind === 'CLUSTER' && item.ownerClusterId != null),
            'the station inside one holds gear its association owns').toBeTruthy()

        const alone = await heldGear(outside)
        expect(alone.some(item => item.ownerKind === 'CLUSTER' && item.ownerClusterId == null),
            'the station outside one holds gear whose owner it can only assert').toBeTruthy()
    })

    /**
     * Room is the feature the two stations were built for. An association hands its stations room out of
     * its own pool; a station standing alone is given room by the instance, and the two are not the same
     * number.
     */
    test('the association gives its station more room than the instance gives the one outside', async ({
        clusterStationManagerPage: inside, standaloneStationManagerPage: outside,
    }) => {
        const withAssociation = await roomFor(inside)
        const alone = await roomFor(outside)

        expect(alone, 'a station on its own is still given room').toBeGreaterThan(0)
        expect(withAssociation, 'and an association hands out more of it than the instance does')
            .toBeGreaterThan(alone)
    })

    /**
     * An association asks questions of the people at its stations, and those questions stand among the
     * station's own. A station outside every association is asked only what it asks itself.
     */
    test('the association asks questions the station outside is never asked', async ({
        clusterStationManagerPage: inside, standaloneStationManagerPage: outside,
    }) => {
        const asked = await questionsAbout(inside)
        expect(asked.some(field => field.origin === 'CLUSTER'),
            'the association asks something of the people at its station').toBeTruthy()

        const askedAlone = await questionsAbout(outside)
        expect(askedAlone.every(field => field.origin === 'STATION'),
            'and nobody asks anything of the station standing alone').toBeTruthy()
    })

    /**
     * The screen that says which association a station belongs to is the same screen either way, and it
     * is the one place the difference is stated rather than implied: one of the two is told it belongs
     * and cannot leave, the other is offered the way in.
     */
    test('one station is told it belongs and the other is offered the way in', async ({
        clusterStationManagerPage: inside, standaloneStationManagerPage: outside,
    }) => {
        await inside.goto('/station/manage/cluster')
        await expect(inside.getByTestId('app-shell')).toBeVisible()
        await expect(inside.getByText('Diese Wache gehört zu diesem Verband.', {exact: false})).toBeVisible()
        await expect(inside.getByRole('button', {name: 'Beitritt beantragen'})).toHaveCount(0)

        await outside.goto('/station/manage/cluster')
        await expect(outside.getByTestId('app-shell')).toBeVisible()
        await expect(outside.getByText('Einem Verband beitreten')).toBeVisible()
        await expect(outside.getByText('Diese Wache gehört zu diesem Verband.', {exact: false})).toHaveCount(0)
    })
})
