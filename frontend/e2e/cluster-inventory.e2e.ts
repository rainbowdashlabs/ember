/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {
    test, expect, apiHeaders, enterCluster, clusterGearManagerPage, clusterStationManager, pageAsThrowaway,
} from './fixtures/auth'
import {MADE_BY_A_STORY, stationUnder} from './fixtures/cluster'
import {must} from './fixtures/must'

/**
 * The cluster's own gear: where each piece is, who may change it, and which steps of a movement only the
 * cluster can answer.
 *
 * The seeder leaves two movements standing on a step the cluster owns, one return and one exchange. They are
 * only ever read: a story that answers the cluster's step starts a movement on a piece of its own, so a retry
 * finds the same ground the first attempt did. Serial, because both ends of the same movement are read and pressed in
 * turn and two workers doing that at once would each be answering what the other just moved on from.
 */
test.describe.configure({mode: 'serial'})

/** The movements waiting on the cluster, read as the cluster. */
async function queue(page: Page, clusterUid: string) {
    const headers = {...await apiHeaders(page), 'X-Cluster-Id': clusterUid}
    const response = await page.request.get('/api/v1/cluster/inventory/queue', {headers})
    expect(response.ok()).toBeTruthy()
    return {headers, entries: await response.json()}
}

/**
 * A piece the cluster owns that the station records for it and that no other story has touched.
 *
 * Written into the station inventory that already keeps the cluster's gear, so it is ordinary in every way
 * except that it is this story's alone: the seeded pieces are what every other story reads and moves, and a
 * story that sends one of them away leaves its retry and its neighbours nothing to find. A sized piece is
 * one that can be swapped for another size, which is what an exchange needs.
 */
async function aPieceOfOurOwn(
    station: Page, headers: Record<string, string>, clusterUid: string, name: string, sized = false,
) {
    const own = await station.request
        .get('/api/v1/inventories', {headers})
        .then(r => r.json())
        .then((inventories: {id: number}[]) => new Set(inventories.map(inventory => inventory.id)))
    const held = await station.request
        .get('/api/v1/inventories/all-items', {headers})
        .then(r => r.json())
    const shelf = must((held as {ownerKind: string; ownerClusterId: number | null; sizeId: number | null;
        inventoryId: number}[])
        .find(item => item.ownerKind === 'CLUSTER' && !!item.ownerClusterId && (item.sizeId !== null) === sized
            && own.has(item.inventoryId)), 'a station inventory keeping the cluster\'s gear')

    const created = await station.request.post(`/api/v1/inventories/${shelf.inventoryId}/items`, {
        headers,
        data: {internalId: `KV-E2E-${test.info().workerIndex}-${Date.now()}`, name, sizeId: shelf.sizeId,
            metadata: null, ownerKind: 'CLUSTER', ownerClusterId: clusterUid},
    })
    expect(created.ok(), 'the station records a piece the cluster owns').toBeTruthy()
    return (await created.json()) as {id: number; inventoryId: number; sizeId: number | null}
}

/** A return the station starts on a piece, which leaves it standing on the cluster's step. */
async function startReturn(station: Page, headers: Record<string, string>,
    piece: {id: number; inventoryId: number}, reason: string): Promise<number> {
    const started = await station.request.post('/api/v1/movements', {
        headers,
        data: {purpose: 'RETURN', outgoingItemId: piece.id, inventoryId: piece.inventoryId, reason},
    })
    expect(started.ok(), 'the station may send back what it holds').toBeTruthy()
    return (await started.json()).movement.id
}

/** One movement as somebody sees it, steps and all. */
async function movement(page: Page, id: number, headers: Record<string, string>) {
    const response = await page.request.get(`/api/v1/movements/${id}`, {headers})
    expect(response.ok(), `movement ${id} should be readable`).toBeTruthy()
    return response.json()
}

test.describe('Cluster inventory', () => {
    /**
     * CLS-35 - The cluster keeps its gear and knows where every piece is.
     *
     * Owning it and holding it are different questions, and the cluster's list answers both: the piece,
     * the station it is at, and whoever has it there. The API says the data is right; the screen, which
     * groups the pieces by station, says somebody can see it.
     */
    test('the cluster sees every piece it owns and where each one is', async ({browser, request}) => {
        const page = await clusterGearManagerPage(browser, request)
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const items = await page.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())
        expect(items.length, 'the demo cluster owns gear').toBeGreaterThan(0)

        const custodies = new Set(items.map((i: {custody: string}) => i.custody))
        expect(custodies.has('AT_STATION') && custodies.has('WITH_MEMBER'),
            'gear resting at a station and gear somebody is wearing are told apart').toBeTruthy()
        expect(items.some((i: {stationName: string}) => !!i.stationName),
            'and each piece says which station it is at').toBeTruthy()

        await page.goto('/cluster/inventory/out')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByTestId('out-station-group').first()).toBeVisible({timeout: 15000})
        await expect(page.getByTestId('out-item').first()).toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-39 - A step belonging to the cluster is not actionable at the station.
     *
     * The station sees the step and who is being waited on, and has no button. Standing in is for an
     * owner that cannot answer at all, which is not this one.
     */
    test('a step belonging to the cluster carries no button at the station',
        async ({browser, request}) => {
            const cluster = await clusterGearManagerPage(browser, request)
            const clusterUid = (await enterCluster(cluster)).uid
            const {entries} = await queue(cluster, clusterUid)
            expect(entries.length, 'the seeder leaves the cluster something to answer').toBeGreaterThan(0)
            const waiting = entries[0]

            const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
            const stationHeaders = await apiHeaders(station)
            const seen = await movement(station, waiting.movementId, stationHeaders)

            const current = seen.steps.find((s: {current: boolean}) => s.current)
            expect(current.actor, 'the step is the owner\'s').toBe('OWNER')
            expect(current.actionable, 'and the station has no button for it').toBeFalsy()

            await station.goto('/station/inventory/movements')
            await expect(station.getByTestId('app-shell')).toBeVisible()
            await station.context().close()
            await cluster.context().close()
        })

    /**
     * CLS-37 - A station hands an item back to the cluster.
     *
     * The station cannot mark the arrival itself; the cluster confirms it, and the record says the owner
     * confirmed rather than that somebody stood in for it.
     */
    test('the cluster confirms the arrival the station cannot', async ({browser, request}) => {
        const page = await clusterGearManagerPage(browser, request)
        const cluster = await enterCluster(page)

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const stationHeaders = await apiHeaders(station)
        const piece = await aPieceOfOurOwn(station, stationHeaders, cluster.uid, 'Jacke zum Zurückgeben')
        const id = await startReturn(station, stationHeaders, piece, 'Wird nicht mehr gebraucht')
        await station.context().close()

        const {headers, entries} = await queue(page, cluster.uid)
        const waiting = entries.find((e: {purpose: string; movementId: number}) =>
            e.purpose === 'RETURN' && e.movementId === id)
        expect(waiting, 'a return is waiting on the cluster').toBeTruthy()

        const before = await movement(page, waiting.movementId, headers)
        const step = before.steps.find((s: {current: boolean}) => s.current)
        expect(step.actionable, 'the cluster may answer its own step').toBeTruthy()

        const answered = await page.request.post(`/api/v1/movements/${waiting.movementId}/acknowledge`,
            {headers, data: {stepId: step.id, note: 'Angekommen'}})
        expect(answered.ok()).toBeTruthy()

        const after = await movement(page, waiting.movementId, headers)
        expect(after.movement.state).toBe('DONE')
        const owned = after.steps.find((s: {id: number}) => s.id === step.id)
        expect(owned.ackKind, 'the owner answered for itself rather than being stood in for').toBe('CONFIRMED')

        await page.goto('/cluster/inventory/movements')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-38 - An exchange walks the whole chain.
     *
     * Each side only its own steps: the station gets no further than the step it owns, and what moves the
     * chain past the cluster's step is the cluster pressing it.
     */
    test('an exchange walks past the cluster only when the cluster answers', async ({browser, request}) => {
        const page = await clusterGearManagerPage(browser, request)
        const cluster = await enterCluster(page)

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const stationHeaders = await apiHeaders(station)
        const piece = await aPieceOfOurOwn(station, stationHeaders, cluster.uid, 'Jacke zum Tauschen', true)
        const members = await station.request
            .get('/api/v1/station-members', {headers: stationHeaders})
            .then(r => r.json())
        const member = must((Array.isArray(members) ? members : members.members ?? [])
            .find((m: {userType: string}) => m.userType === 'MEMBER'), 'an ordinary member at the station')
        const handed = await station.request.put(`/api/v1/inventory-items/${piece.id}/assign`,
            {headers: stationHeaders, data: {memberId: member.id, memberName: null}})
        expect(handed.ok(), 'the member wears the piece that is to be exchanged').toBeTruthy()

        const started = await station.request.post('/api/v1/movements', {
            headers: stationHeaders,
            data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: piece.id,
                inventoryId: piece.inventoryId, oldSizeId: piece.sizeId, newSizeId: piece.sizeId,
                reason: 'Reißverschluss kaputt'},
        })
        expect(started.ok(), 'the station raises the exchange').toBeTruthy()
        let walked = await started.json()
        for (let guard = 6; guard > 0; guard -= 1) {
            const current = walked.steps.find((s: {current: boolean}) => s.current)
            if (!current?.actionable) break
            const next = await station.request.post(`/api/v1/movements/${walked.movement.id}/acknowledge`,
                {headers: stationHeaders, data: {stepId: current.id, note: ''}})
            expect(next.ok()).toBeTruthy()
            walked = await movement(station, walked.movement.id, stationHeaders)
        }
        await station.context().close()

        const {headers, entries} = await queue(page, cluster.uid)
        const waiting = entries.find((e: {purpose: string; movementId: number}) =>
            e.purpose === 'EXCHANGE' && e.movementId === walked.movement.id)
        expect(waiting, 'an exchange is waiting on the cluster').toBeTruthy()

        const before = await movement(page, waiting.movementId, headers)
        const step = before.steps.find((s: {current: boolean}) => s.current)
        const behind = before.steps.filter((s: {position: number}) => s.position < step.position)
        expect(behind.length, 'the station has already walked its own steps').toBeGreaterThan(0)
        expect(behind.every((s: {ackKind: string}) => !!s.ackKind), 'and each is stamped').toBeTruthy()

        const answered = await page.request.post(`/api/v1/movements/${waiting.movementId}/acknowledge`,
            {headers, data: {stepId: step.id, note: 'Ersatz unterwegs'}})
        expect(answered.ok()).toBeTruthy()

        const after = await movement(page, waiting.movementId, headers)
        const moved = after.movement.state === 'DONE'
            || after.steps.find((s: {current: boolean}) => s.current)?.position > step.position
        expect(moved, 'the chain moved on once the cluster answered').toBeTruthy()
        await page.context().close()
    })

    /**
     * CLS-36 - A station hands cluster gear to a member and takes it back.
     *
     * Where a piece is remains the station's to say, and the cluster watches it change hands without doing
     * anything. Handing somebody a jacket across a table is not a request.
     */
    test('the station hands cluster gear over and back, and the cluster sees it', async ({browser, request}) => {
        const page = await clusterGearManagerPage(browser, request)
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const stationHeaders = await apiHeaders(station)
        const resting = await aPieceOfOurOwn(station, stationHeaders, cluster.uid, 'Jacke zum Ausgeben')
        const members = await station.request
            .get('/api/v1/station-members', {headers: stationHeaders})
            .then(r => r.json())
        const member = (Array.isArray(members) ? members : members.members ?? [])
            .find((m: {userType: string}) => m.userType === 'MEMBER')

        const handed = await station.request.put(`/api/v1/inventory-items/${resting.id}/assign`,
            {headers: stationHeaders, data: {memberId: member.id, memberName: null}})
        expect(handed.ok(), 'the station may hand out what it holds').toBeTruthy()

        const withMember = await page.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())
            .then((items: {id: number; custody: string}[]) => must(items.find(i => i.id === resting.id),
                'the piece, as the cluster sees it'))
        expect(withMember.custody, 'and the cluster sees it without being asked').toBe('WITH_MEMBER')

        const back = await station.request.put(`/api/v1/inventory-items/${resting.id}/assign`,
            {headers: stationHeaders, data: {memberId: null, memberName: null}})
        expect(back.ok()).toBeTruthy()

        const returned = await page.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())
            .then((items: {id: number; custody: string}[]) => must(items.find(i => i.id === resting.id),
                'the piece, back where it came from'))
        expect(returned.custody, 'and back again, with no movement anywhere').toBe('AT_STATION')

        await station.context().close()
        await page.context().close()
    })

    /**
     * CLS-40 - An owner that does not use Ember is stood in for.
     *
     * The contrast that makes the rest legible: where the owner cannot answer, the station answers for it,
     * and the record says asserted rather than confirmed so the difference survives.
     *
     * The presets carry no owner leg, being what a station falls back to, so the story adds a chain that
     * records it and has the station walk the owner's steps itself.
     */
    test('a station stands in for an owner that does not run here', async ({browser, request}) => {
        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const headers = await apiHeaders(station)

        const items = await station.request
            .get('/api/v1/inventories/all-items', {headers})
            .then(r => r.json())
        const offSystem = (Array.isArray(items) ? items : items.items ?? [])
            .find((i: {ownerKind: string; ownerClusterId: number | null; custody: string}) =>
                i.ownerKind === 'CLUSTER' && !i.ownerClusterId && i.custody === 'AT_STATION')
        expect(offSystem, 'the demo keeps a piece owned by a body that is not on this instance').toBeTruthy()

        const flow = await station.request.post('/api/v1/movement-flows',
            {headers, data: {name: `Rückgabe mit Trägerbein ${Date.now()}`, purpose: 'RETURN'}})
        expect(flow.ok()).toBeTruthy()
        const flowId = (await flow.json()).id

        for (const step of [
            {label: 'Wache schickt zurück', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'IN_TRANSIT', picksItem: false},
            {label: 'Träger nimmt an', actor: 'OWNER', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        ]) {
            const added = await station.request.post(`/api/v1/movement-flows/${flowId}/steps`,
                {headers, data: step})
            expect(added.ok()).toBeTruthy()
        }

        const bound = await station.request.put('/api/v1/movement-flow-bindings', {
            headers,
            data: {inventoryId: offSystem.inventoryId, ownerKind: 'CLUSTER', purpose: 'RETURN', flowId},
        })
        expect(bound.ok()).toBeTruthy()

        const started = await station.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'RETURN', outgoingItemId: offSystem.id, inventoryId: offSystem.inventoryId,
                reason: 'Zurück an den Träger'},
        })
        expect(started.ok()).toBeTruthy()
        const detail = await started.json()

        let current = detail.steps.find((s: {current: boolean}) => s.current)
        for (let guard = 6; guard > 0 && current; guard -= 1) {
            expect(current.actionable, 'the station may answer where the owner cannot').toBeTruthy()
            const next = await station.request.post(`/api/v1/movements/${detail.movement.id}/acknowledge`,
                {headers, data: {stepId: current.id, note: ''}})
            expect(next.ok()).toBeTruthy()
            const seen = await movement(station, detail.movement.id, headers)
            if (seen.movement.state !== 'OPEN') {
                const owner = seen.steps.filter((s: {actor: string}) => s.actor === 'OWNER')
                expect(owner.length, 'the chain had a step for the owner').toBeGreaterThan(0)
                expect(owner.every((s: {ackKind: string}) => s.ackKind === 'ASSERTED'),
                    'and the station standing in is recorded as asserted, not confirmed').toBeTruthy()
                const own = seen.steps.filter((s: {actor: string}) => s.actor === 'STATION')
                expect(own.every((s: {ackKind: string}) => s.ackKind === 'CONFIRMED'),
                    'while its own steps read as confirmed').toBeTruthy()
                break
            }
            current = seen.steps.find((s: {current: boolean}) => s.current)
        }

        await station.context().close()
    })

    /**
     * CLS-41 - The cluster declines a movement.
     *
     * It closes as declined with the reason readable at the station, and the gear goes back to whoever had
     * it before rather than staying in limbo.
     */
    test('the cluster declines, with the reason readable at the station', async ({browser, request}) => {
        const page = await clusterGearManagerPage(browser, request)
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const stationHeaders = await apiHeaders(station)
        const owned = await aPieceOfOurOwn(station, stationHeaders, cluster.uid, 'Jacke zum Behalten')
        const id = await startReturn(station, stationHeaders, owned, 'Wird nicht mehr gebraucht')

        const declined = await page.request.post(`/api/v1/movements/${id}/decline`,
            {headers, data: {reason: 'Behaltet es noch'}})
        expect(declined.ok()).toBeTruthy()

        const seen = await movement(station, id, stationHeaders)
        expect(seen.movement.state).toBe('DECLINED')
        expect(seen.movement.closeReason, 'why it was refused, not why it was started')
            .toContain('Behaltet es noch')

        const item = await station.request
            .get(`/api/v1/inventory-items/${owned.id}`, {headers: stationHeaders})
            .then(r => r.json())
        expect(item.custody, 'the gear is where it was before anybody asked').toBe('AT_STATION')

        await station.context().close()
        await page.context().close()
    })

    /**
     * CLS-42 - A cluster-owned item cannot be changed or passed on at the station.
     *
     * What it is stays with whoever owns it. Where it is remains the station's to say, which is the other
     * half of the same idea and is what the custody stories walk.
     *
     * Besides the server's refusals, the screen must never offer the edit at all, since a refusal after
     * typing is the same no delivered late; it offers the two things a station may do instead.
     */
    test('a cluster-owned item is not the station\'s to rename or lend', async ({browser, request}) => {
        const page = await clusterGearManagerPage(browser, request)
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}
        const items = await page.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())
        const at = items.find((i: {custody: string}) => i.custody === 'AT_STATION')
        expect(at, 'the cluster has gear resting at a station').toBeTruthy()

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const stationHeaders = await apiHeaders(station)

        const renamed = await station.request.put(`/api/v1/inventory-items/${at.id}`,
            {headers: stationHeaders, data: {name: 'Umbenannt', internalId: at.internalId}})
        expect(renamed.ok(), 'the station may not rename what it does not own').toBeFalsy()

        const removed = await station.request.delete(`/api/v1/inventory-items/${at.id}`,
            {headers: stationHeaders})
        expect(removed.ok(), 'nor delete it').toBeFalsy()

        await station.goto(`/station/inventory/item/${at.id}`)
        await expect(station.getByTestId('app-shell')).toBeVisible()
        await expect(station.getByTestId('item-edit'),
            'no pencil, because this is not the station\'s to describe').toHaveCount(0)

        await expect(station.getByTestId('owned-elsewhere')).toBeVisible({timeout: 15000})

        await station.context().close()
        await page.context().close()
    })

    /**
     * CLS-105 - Handing a piece of the association's gear back is pressed, not merely offered.
     *
     * CLS-42 asserts the panel holding the two actions is on screen and stops there, so the movement
     * either of them starts was proven by nothing: the buttons could have been wired to nothing and
     * every story would have stayed green. This presses one and follows it to the other end, which is
     * the whole point, because a button that starts nothing looks the same from the station.
     */
    test('a station hands a piece of the association gear back from the item itself',
        async ({browser, request}) => {
            const page = await clusterGearManagerPage(browser, request)
            const cluster = await enterCluster(page)
            const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}
            const items = await page.request
                .get('/api/v1/cluster/inventory/items', {headers})
                .then(r => r.json())
            const at = items.find((i: {custody: string}) => i.custody === 'AT_STATION')
            expect(at, 'the association has gear resting at a station').toBeTruthy()

            const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
            await station.goto(`/station/inventory/item/${at.id}`)
            await expect(station.getByTestId('owned-elsewhere')).toBeVisible({timeout: 15000})

            const reason = `Passt nicht mehr ${Date.now()}`
            await station.getByRole('button', {name: 'Zurückgeben'}).click()
            await expect(station.getByTestId('movement-wizard')).toBeVisible()
            await station.getByRole('textbox').last().fill(reason)
            await station.getByTestId('wizard-next').click()
            await station.getByTestId('wizard-start').click()

            await expect(async () => {
                const queue = await page.request
                    .get('/api/v1/cluster/inventory/queue', {headers})
                    .then(r => r.json())
                expect(queue.some((entry: {purpose: string; itemName: string}) =>
                    entry.purpose === 'RETURN' && entry.itemName === at.name),
                'the association is now being asked to take it back').toBeTruthy()
            }).toPass({timeout: 30000})

            await station.context().close()
            await page.context().close()
        })

    /**
     * CLS-43 - Cluster requirements are counted at the station.
     *
     * A piece the cluster owns and the member holds counts towards what that member is supposed to have,
     * because who owns it was never the question a requirement asks.
     *
     * The station's own requirement screen is looked at rather than asked about: the cluster's
     * requirement stands among the station's, named, with nothing on it to press, since it is changed
     * where it was written.
     */
    test('gear the cluster owns counts towards what a member should hold', async ({browser, request}) => {
        const page = await clusterGearManagerPage(browser, request)
        const cluster = await enterCluster(page)
        const headers = {...await apiHeaders(page), 'X-Cluster-Id': cluster.uid}
        const items = await page.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())
        const held = items.find((i: {custody: string; holderName: string}) =>
            i.custody === 'WITH_MEMBER' && !!i.holderName)
        expect(held, 'a member is wearing something the cluster owns').toBeTruthy()

        const station = await pageAsThrowaway(browser, request, [], await clusterStationManager(request))
        const stationHeaders = await apiHeaders(station)
        const mine = await station.request
            .get(`/api/v1/inventory-items/${held.id}`, {headers: stationHeaders})
            .then(r => r.json())
        expect(mine.assignedTo, 'the station sees who has it').toBeTruthy()
        expect(mine.ownerKind, 'and that the cluster owns it').toBe('CLUSTER')

        await station.goto('/station/inventory/requirements')
        await expect(station.getByTestId('app-shell')).toBeVisible()
        await expect(station.getByRole('button', {name: /hinzufügen/i}).first())
            .toBeVisible({timeout: 15000})

        const fromCluster = station.getByTestId('cluster-requirement').first()
        await expect(fromCluster).toBeVisible({timeout: 15000})
        await expect(fromCluster.getByTestId('cluster-requirement-badge')).toHaveText(cluster.name)
        await expect(fromCluster.getByTestId('cluster-requirement-quantity')).toHaveText(/\d+/)

        await expect(fromCluster.getByRole('button')).toHaveCount(0)
        await expect(fromCluster.getByRole('spinbutton')).toHaveCount(0)

        await station.context().close()
        await page.context().close()
    })

    /**
     * CLS-44 - Releasing a station brings the gear home.
     *
     * What the cluster owns does not stay behind with a station that no longer answers to it. Walked on a
     * station the story makes and then lets go of, because letting a seeded one go would take the subject
     * of every other cluster story with it.
     */
    test('releasing a station brings the cluster\'s gear home', async ({adminPage: admin, browser, request}) => {
        const cluster = await enterCluster(admin)
        const headers = {...await apiHeaders(admin), 'X-Cluster-Id': cluster.uid}

        const station = await stationUnder(admin, browser, request, headers, `${MADE_BY_A_STORY}Abgabe ${Date.now()}`)
        const stationHeaders = await apiHeaders(station.page)

        const inventory = await station.page.request.post('/api/v1/inventories',
            {headers: stationHeaders, data: {name: 'Einsatzkleidung', inventoryType: 'MIXED', hasSizes: false}})
        expect(inventory.ok()).toBeTruthy()
        const inventoryId = (await inventory.json()).id

        const code = `KV-E2E-${Date.now()}`
        const item = await station.page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
            headers: stationHeaders,
            data: {internalId: code, name: 'Jacke', sizeId: null, metadata: null,
                ownerKind: 'CLUSTER', ownerClusterId: cluster.uid},
        })
        expect(item.ok(), 'the station records a piece the cluster owns').toBeTruthy()
        const itemId = (await item.json()).id

        const owned = await admin.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())
            .then((items: {id: number; stationName: string}[]) => items.find(i => i.id === itemId))
        expect(owned?.stationName, 'and the cluster sees it at that station').toBe(station.name)

        const released = await admin.request.delete(`/api/v1/cluster/stations/${station.uid}`, {headers})
        expect(released.ok()).toBeTruthy()

        const home = await admin.request
            .get('/api/v1/cluster/inventory/items', {headers})
            .then(r => r.json())
            .then((items: {id: number; custody: string; stationName: string}[]) =>
                must(items.find(i => i.id === itemId), 'the piece the cluster still owns'))
        expect(home.custody, 'and it is back in the cluster\'s own store').toBe('WITH_OWNER')
        expect(home.stationName, 'held at no station any more').toBeFalsy()

        const left = await station.page.request
            .get('/api/v1/inventories/all-items', {headers: stationHeaders})
            .then(r => r.json())
        expect(JSON.stringify(left), 'and the station no longer lists it').not.toContain(code)

        await station.page.context().close()
    })
})
