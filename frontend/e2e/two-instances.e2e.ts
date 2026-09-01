/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, homeInternalUrl, peerInternalUrl} from './fixtures/peer'
import {unique} from './fixtures/unique'

/**
 * Two installations of the application, not two stations of one.
 *
 * Everything federation does happens between instances, so a suite with one instance can only ever
 * tell the story of a station talking to a station beside it in the same database. These two
 * stories are the ground the federation stories stand on: that the second instance is genuinely a
 * second one, and that each of them can call the other over the network.
 */
test.describe('Two instances', () => {
    /**
     * The second instance keeps its own stations.
     *
     * Made rather than merely counted: both instances run the same seeder, so a story comparing
     * what they were born with would compare two lists of the same names and prove nothing about
     * where the rows live. A station that exists on one and not on the other does prove it.
     *
     * The first instance's own stations are counted as well, so that the missing one means what it
     * says: an instance answering with nothing at all would pass the same assertion for the wrong
     * reason.
     */
    test('a station made on the second instance is unknown to the first', async ({
        peerAdminApi,
        homeAdminApi,
    }) => {
        const name = unique('E2E-Zweite-Instanz')

        const created = await peerAdminApi.post('/api/v1/stations', {data: {name}})
        expect(created.status()).toBe(201)
        const {id: uid} = await created.json()
        expect(uid).toBeTruthy()

        const onPeer = await peerAdminApi.get('/api/v1/stations')
        expect(onPeer.ok()).toBe(true)
        expect((await onPeer.json()).map((station: {id: string}) => station.id)).toContain(uid)

        const onHome = await homeAdminApi.get('/api/v1/stations')
        expect(onHome.ok()).toBe(true)
        const homeStations: {id: string}[] = await onHome.json()
        expect(homeStations.map(station => station.id)).not.toContain(uid)
        expect(homeStations.length).toBeGreaterThan(0)
    })

    /**
     * Each instance calls the other and gets the other one back.
     *
     * The probe is the cheapest call one instance makes to another: an administrator names an
     * address, the server fetches that address itself and reports what answered. That the answer
     * carries the far instance's identity and not the near one's is what says a real network hop
     * happened, and the address it goes to is the one a container can resolve - the port on the
     * host, which is how the stories reach both, is the caller itself as far as a container is
     * concerned.
     *
     * The address each one publishes is checked against the one its caller used, because that is
     * what an invitation or a peer entry carries: a base address that only the instance itself can
     * resolve is the usual reason such a setup works on the second attempt rather than the first.
     */
    test('each instance reaches the other over HTTP', async ({peerAdminApi, homeAdminApi}) => {
        const homeIdentity = await homeAdminApi.get('/api/v1/admin/discovery/identity')
        expect(homeIdentity.ok()).toBe(true)
        const home = await homeIdentity.json()

        const peerIdentity = await peerAdminApi.get('/api/v1/admin/discovery/identity')
        expect(peerIdentity.ok()).toBe(true)
        const peer = await peerIdentity.json()

        expect(peer.instanceId).toBeTruthy()
        expect(peer.instanceId).not.toBe(home.instanceId)

        const fromPeer = await peerAdminApi.post('/api/v1/admin/discovery/peers/probe', {
            data: {baseUrl: homeInternalUrl()},
        })
        expect(fromPeer.ok()).toBe(true)
        expect((await fromPeer.json()).instanceId).toBe(home.instanceId)

        const fromHome = await homeAdminApi.post('/api/v1/admin/discovery/peers/probe', {
            data: {baseUrl: peerInternalUrl()},
        })
        expect(fromHome.ok()).toBe(true)
        const answered = await fromHome.json()
        expect(answered.instanceId).toBe(peer.instanceId)
        expect(answered.baseUrl).toBe(peerInternalUrl())
    })
})
