/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Browser, Page} from '@playwright/test'
import {apiHeaders} from './auth'

/**
 * A cluster of the story's own, with a station under it and somebody who runs that station.
 *
 * Governance reaches every member station at once: denying a module or locking a colour is not something
 * that can be done to the seeded cluster for a second and undone, because four workers are reading those
 * stations while it is happening. A story that governs builds what it governs.
 *
 * Only an instance administrator can do this, which is also the point: creating a cluster and appointing
 * its first person is exactly what the instance is for, and every step below is a call somebody makes in
 * the product rather than a fixture reaching behind it.
 */
/**
 * What every cluster a story builds is called first.
 *
 * Stories run four at a time and several of them make a cluster, so "the cluster this account may act
 * for" stops meaning anything unless the made-up ones can be told from the seeded one by looking. This
 * is how they are told apart, and it is a prefix rather than a suffix so it reads at a glance.
 */
export const MADE_BY_A_STORY = 'E2E-'

export interface OwnCluster {
    uid: string
    name: string
    /** Headers that act for this cluster, for the calls a story makes rather than clicks. */
    headers: Record<string, string>
    /** The station under it. */
    stationUid: string
    stationName: string
    /** A page signed in as the person who runs that station. */
    stationPage: Page
}

/**
 * Builds one, on the account the given page holds.
 *
 * @param page    an instance administrator's page
 * @param browser to open the station manager's own context with
 * @param request for the demo login the station manager needs
 * @param label   what to call the things it makes, so a failure names the story that made them
 */
export async function ownCluster(
    page: Page,
    browser: Browser,
    request: APIRequestContext,
    label: string,
): Promise<OwnCluster> {
    const stamp = `${MADE_BY_A_STORY}${label} ${Date.now()}`
    const headers = await apiHeaders(page)

    const created = await page.request.post('/api/v1/clusters', {headers, data: {name: stamp, description: null}})
    if (!created.ok()) throw new Error(`Creating a cluster answered ${created.status()}`)
    const cluster = await created.json()

    const me = await page.request.get('/api/v1/session', {headers})
    const accountUid = (await me.json()).account.uid
    const appointed = await page.request.post(`/api/v1/clusters/${cluster.uid}/administrators`,
        {headers, data: {accountUid}})
    if (!appointed.ok()) throw new Error(`Appointing an administrator answered ${appointed.status()}`)

    const actingHeaders = {...headers, 'X-Cluster-Id': cluster.uid}
    const station = await stationUnder(page, browser, request, actingHeaders, `Wache ${stamp}`)

    return {
        uid: cluster.uid,
        name: stamp,
        headers: actingHeaders,
        stationUid: station.uid,
        stationName: station.name,
        stationPage: station.page,
    }
}

/**
 * A station under a given cluster, set up, with a page signed in as the person who runs it.
 *
 * A cluster makes a station and nobody is at it: it has not been through the assistant, it has no manager,
 * and a story that wants to look at it as a station has to do both. Only an instance administrator can,
 * which is also what makes it honest: every step is a call somebody makes in the product.
 *
 * @param page    an instance administrator's page
 * @param browser to open the manager's own context with
 * @param request for the demo login the manager needs
 * @param headers headers already acting for the cluster the station should belong to
 * @param name    what to call it
 */
export async function stationUnder(
    page: Page,
    browser: Browser,
    request: APIRequestContext,
    headers: Record<string, string>,
    name: string,
): Promise<{uid: string; name: string; page: Page}> {
    const made = await page.request.post('/api/v1/cluster/stations', {headers, data: {name}})
    if (!made.ok()) throw new Error(`Creating a station answered ${made.status()}`)
    const station = await made.json()

    // A brand new person rather than a spare seeded one: being made a station's manager rewrites what an
    // account may do, and every seeded account is one some other story is acting as. Naming an address
    // nobody has creates it, which is the same path an invitation takes.
    const managerEmail = `wache-${name.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}@e2e.ember`
    const assigned = await page.request.put(`/api/v1/stations/${station.uid}`,
        {headers: await apiHeaders(page), data: {name, managerEmail}})
    if (!assigned.ok()) throw new Error(`Assigning a manager answered ${assigned.status()}`)

    // A station the cluster has just made has not been set up, and its manager is sent into the assistant
    // before anything else. The story is not about the assistant, so it is walked the way the assistant
    // walks it: the one required step it has, and then finishing.
    const login = await request.post('/api/v1/demo/login', {data: {email: managerEmail}})
    if (!login.ok()) throw new Error(`Demo login for ${managerEmail} answered ${login.status()}`)
    const token = (await login.json()).token
    const asManager = {Authorization: `Bearer ${token}`, 'X-Station-Id': station.uid}

    const located = await request.put('/api/v1/station/location', {
        headers: asManager,
        data: {
            addressLine: 'Musterstraße 1', postalCode: '12345', city: 'Musterstadt',
            country: 'DE', latitude: 52.52, longitude: 13.405,
        },
    })
    if (!located.ok()) throw new Error(`Setting the address answered ${located.status()}`)
    const setupDone = await request.post('/api/v1/station/setup/complete', {headers: asManager})
    if (!setupDone.ok()) throw new Error(`Finishing the setup answered ${setupDone.status()}`)

    const context = await browser.newContext()
    await context.addInitScript(([sessionToken, stationId]) => {
        window.localStorage.setItem('session_token', sessionToken)
        window.localStorage.setItem('station_id', stationId)
        window.localStorage.setItem('storage_consent', 'accepted')
        window.localStorage.setItem('onboarding_tour_completed', 'true')
    }, [token, station.uid])

    return {uid: station.uid, name, page: await context.newPage()}
}
