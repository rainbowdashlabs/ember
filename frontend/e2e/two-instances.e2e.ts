/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    expect,
    homeInternalUrl,
    homePublishedUrl,
    instanceRequestAs,
    peerBaseUrl,
    peerInternalUrl,
    stationManagerOf,
    test,
} from './fixtures/peer'
import {proveFreshly} from './fixtures/auth'
import {unique} from './fixtures/unique'
import type {APIRequestContext} from '@playwright/test'

/** What a station says about a partner. Only the parts a story here reads. */
interface Partner {
    status: string
    remoteHost: string | null
    partnerStationId: string
}

/** Every partner of the station this context acts for. */
async function partnersOf(api: APIRequestContext): Promise<Partner[]> {
    const response = await api.get('/api/v1/federation/partners')
    if (!response.ok()) throw new Error(`The partner list answered ${response.status()}`)
    return (await response.json()).map((entry: {partner: Partner}) => entry.partner)
}

/**
 * The one partner naming the given station, of all this station has.
 *
 * Asked for by identity rather than by name: both instances are seeded from the same list, so a
 * station of the second is called exactly what a station of the first is called, and only the
 * identity tells the two apart.
 */
async function partnerWith(api: APIRequestContext, stationUid: string): Promise<Partner> {
    const matching = (await partnersOf(api)).filter(partner => partner.partnerStationId === stationUid)
    if (matching.length !== 1) throw new Error(`${matching.length} partners name station ${stationUid}, expected one`)
    return matching[0]
}

/**
 * Two installations of the application, not two stations of one.
 *
 * Everything federation does happens between instances, so a suite with one instance can only ever
 * tell the story of a station talking to a station beside it in the same database. These stories are
 * the ground the federation stories stand on: that the second instance is genuinely a second one,
 * that each of them can call the other over the network, and that two of their stations pair up
 * over it.
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

    /**
     * Two stations on two instances become partners from an invite code, and both sides say so.
     *
     * This is the whole point of a second instance. A code names the station and the address it
     * lives at; the station entering it calls that address, the issuing side redeems the token and
     * answers with its own half, and each writes a partner row naming the other. Nothing of that
     * happens between two stations of one instance, where the same exchange is a method call.
     *
     * The code travels from the second instance to the first and not the other way, because the
     * first instance publishes the address its browser needs and a container cannot reach that one.
     *
     * The inviting station is made for the story rather than taken from the seed. Both instances are
     * seeded from the same list and derive a station's identity from its name, so the seeded
     * stations of one are the seeded stations of the other by identity, and a station handed such a
     * code is told it is being invited to federate with itself. Its manager is a seeded account
     * handed the fresh station, because the federation routes ask for a fresh proof and a manager
     * made mid-story holds nothing to prove itself with: no password until its setup mail, no
     * second factor ever.
     */
    test('a station takes up an invite code from the other instance', async ({peerAdminApi, homeManagerApi}) => {
        const name = unique('E2E-Gegenstelle')
        const manager = await stationManagerOf(peerBaseUrl())
        const created = await peerAdminApi.post('/api/v1/stations', {data: {name, managerEmail: manager.email}})
        expect(created.status()).toBe(201)
        const {id: invitingStation} = await created.json()

        const inviting = await instanceRequestAs(peerBaseUrl(), {email: manager.email, stationId: invitingStation})
        try {
            await proveFreshly(inviting)
            const invited = await inviting.post('/api/v1/federation/invite')
            expect(invited.ok()).toBe(true)
            const {inviteCode} = await invited.json()
            expect(inviteCode).toContain('ember-')

            await proveFreshly(homeManagerApi)
            const accepted = await homeManagerApi.post('/api/v1/federation/accept', {data: {inviteCode}})
            expect(accepted.status(), await accepted.text()).toBe(201)

            const here = await partnerWith(homeManagerApi, invitingStation)
            expect(here.remoteHost).toBe(peerInternalUrl())
            expect(here.status).not.toBe('PENDING')

            const there = await partnersOf(inviting)
            expect(there).toHaveLength(1)
            expect(there[0].remoteHost).toBe(homePublishedUrl())
            expect(there[0].status).not.toBe('PENDING')
            expect(there[0].partnerStationId).not.toBe(invitingStation)
        } finally {
            await inviting.dispose()
        }
    })

    /**
     * A file of an event travels from the instance that owns it to the instance that reads it.
     *
     * <p>This is the story a single instance cannot tell. Between two stations of one database the
     * owning station's answer is a method call; here it crosses the wire, where the channel carries
     * text and the bytes have to survive being written as text and read back. The reading side is
     * handed exactly what the owning side decided: the open file with its bytes intact, and a
     * refusal for the one kept back, asked for by the id it never saw.
     */
    test('a partner instance is handed the open file of a shared event', async ({
        peerAdminApi,
        homeManagerApi,
    }) => {
        const manager = await stationManagerOf(peerBaseUrl())
        const created = await peerAdminApi.post('/api/v1/stations', {
            data: {name: unique('E2E-Dateiwache'), managerEmail: manager.email},
        })
        expect(created.status()).toBe(201)
        const {id: owningStation} = await created.json()

        const owner = await instanceRequestAs(peerBaseUrl(), {email: manager.email, stationId: owningStation})
        try {
            await proveFreshly(owner)
            const invited = await owner.post('/api/v1/federation/invite')
            expect(invited.ok()).toBe(true)
            const {inviteCode} = await invited.json()

            await proveFreshly(homeManagerApi)
            const accepted = await homeManagerApi.post('/api/v1/federation/accept', {data: {inviteCode}})
            expect(accepted.status(), await accepted.text()).toBe(201)

            const eventId = await eventSharedWithEveryPartner(owner)
            const fileId = await uploadedFile(owner)
            const openLabel = unique('Laufzettel')
            const internalLabel = unique('Einsatzplan')
            const open = await attachedFile(owner, eventId, fileId, openLabel, false)
            const internal = await attachedFile(owner, eventId, fileId, internalLabel, true)

            const listed = await homeManagerApi.get(
                `/api/v1/federated/${owningStation}/events/${eventId}/attachments`)
            expect(listed.ok(), await listed.text()).toBe(true)
            const names = ((await listed.json()) as {name: string}[]).map(file => file.name)
            expect(names).toEqual([openLabel])

            const handed = await homeManagerApi.get(
                `/api/v1/federated/${owningStation}/events/${eventId}/attachments/${open}/file`)
            expect(handed.ok(), await handed.text()).toBe(true)
            expect((await handed.body()).length, 'the bytes survived the crossing').toBe(PIXEL.length)

            const refused = await homeManagerApi.get(
                `/api/v1/federated/${owningStation}/events/${eventId}/attachments/${internal}/file`)
            expect(refused.ok(), 'the owning instance refuses what its event keeps back').toBe(false)
        } finally {
            await owner.dispose()
        }
    })
})

/** The smallest thing the media library accepts: one transparent pixel. */
const PIXEL = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
    'base64',
)

/** A file in the station's own library, since a station made mid-story starts with none. */
async function uploadedFile(api: APIRequestContext): Promise<number> {
    const uploaded = await api.post('/api/v1/media/files', {
        multipart: {file: {name: 'laufzettel.png', mimeType: 'image/png', buffer: PIXEL}},
    })
    expect(uploaded.status(), await uploaded.text()).toBe(201)
    return (await uploaded.json()).id
}

/** An event of the station, shared with every partner it has. */
async function eventSharedWithEveryPartner(api: APIRequestContext): Promise<number> {
    const start = new Date(Date.now() + 86_400_000).toISOString()
    const created = await api.post('/api/v1/events', {
        data: {
            name: unique('Geteilter Abend'),
            description: '',
            eventType: 'ONE_TIME',
            startTime: start,
            endTime: start,
        },
    })
    expect(created.status(), await created.text()).toBe(201)
    const eventId = (await created.json()).id

    const shared = await api.put(`/api/v1/events/${eventId}/federation`, {
        data: {scope: 'ALL_PARTNERS', partnerIds: []},
    })
    expect(shared.ok(), await shared.text()).toBe(true)
    return eventId
}

async function attachedFile(
    api: APIRequestContext,
    eventId: number,
    fileId: number,
    label: string,
    internal: boolean,
): Promise<number> {
    const attached = await api.post(`/api/v1/events/${eventId}/attachments`, {data: {fileId, label, internal}})
    expect(attached.status(), await attached.text()).toBe(201)
    return (await attached.json()).id
}
