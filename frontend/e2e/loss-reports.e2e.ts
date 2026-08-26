/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {
    test,
    expect,
    apiHeaders,
    clusterAccountWith,
    clusterHeaders,
    clusterPage,
    clusterStationManager,
    demoAccounts,
    pageAsThrowaway,
    theSeededCluster,
} from './fixtures/auth'

/**
 * Losing a piece of the association's gear, which is three acts and not one.
 *
 * Marking it missing happens at the station and travels nowhere. Reporting it is a request for a
 * replacement and the only thing the association hears. Answering that request is the association's, and
 * it may refuse. These stories walk each of the three on the screens that carry them.
 */

/** The association that owns gear at this station, read off a piece it already owns here. */
async function owningCluster(page: Page, headers: Record<string, string>): Promise<number> {
    const items = await page.request.get('/api/v1/inventories/all-items', {headers}).then(r => r.json())
    const owned = items.find((i: {ownerKind: string; ownerClusterId: number | null}) =>
        i.ownerKind === 'CLUSTER' && i.ownerClusterId != null)
    expect(owned, 'this station holds gear the association owns').toBeTruthy()
    return owned.ownerClusterId
}

/**
 * A piece of the association's gear in one member's hands, made for one story so no other story loses
 * theirs. Arrangement, not the story: what is walked is what happens to it afterwards.
 */
async function clusterGearFor(page: Page, headers: Record<string, string>, memberId: number, label: string) {
    const clusterId = await owningCluster(page, headers)
    const inventories = await page.request.get('/api/v1/inventories', {headers}).then(r => r.json())
    const holder = inventories.find((i: {inventoryType: string}) => i.inventoryType !== 'INTERNAL')
    expect(holder, 'the station keeps an inventory that may hold the association gear').toBeTruthy()

    // Named for this story alone: every list these stories read is shared with the rest of the suite,
    // and two jackets called the same thing are two rows nobody can tell apart
    const internalId = `${label}-${Date.now()}`
    const name = `Einsatzjacke ${internalId}`
    const made = await page.request.post(`/api/v1/inventories/${holder.id}/items`, {
        headers,
        data: {internalId, name, sizeId: null, metadata: null,
            ownerKind: 'CLUSTER', ownerClusterId: clusterId},
    })
    expect(made.ok(), `the station records a piece of the association gear (${made.status()}: ${await made.text()})`)
        .toBeTruthy()
    const item = await made.json()

    const assigned = await page.request.put(`/api/v1/inventory-items/${item.id}/assign`, {
        headers,
        data: {memberId, memberName: ''},
    })
    expect(assigned.ok(), 'and hands it to the member the story is about').toBeTruthy()
    return {...item, internalId, name}
}

/** The member behind a signed-in page, which is who their own gear is assigned to. */
async function ownMemberId(page: Page, headers: Record<string, string>): Promise<number> {
    const session = await page.request.get('/api/v1/session', {headers}).then(r => r.json())
    expect(session.member, 'this page belongs to somebody at a station').toBeTruthy()
    return session.member.id
}

/** The card for one piece on the member's own inventory screen. */
function cardFor(page: Page, internalId: string) {
    return page.getByTestId('inventory-item-card').filter({hasText: internalId})
}

test.describe('Losing a piece of the association gear', () => {
    // One at a time, and with room for three browser contexts each: what an association demands of a
    // loss report is a setting for the whole association, so a story that changes it changes what every
    // other story here is answered with.
    test.describe.configure({mode: 'serial', timeout: 120_000})

    /**
     * Back to demanding nothing, whatever a story that did not finish left behind.
     *
     * <p>The setting belongs to the association rather than to a story, so a run that stopped half way
     * through the one that changes it would otherwise leave every later run answered differently.
     */
    test.beforeAll(async ({browser, request}) => {
        const gearManager = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const page = await clusterPage(browser, request, gearManager)
        const cluster = await theSeededCluster(page)
        await page.request.put('/api/v1/cluster/inventory/loss-report', {
            headers: await clusterHeaders(page, cluster),
            data: {requires: 'NOTHING'},
        })
        await page.context().close()
    })

    /**
     * CLS-52a - A member reports their own jacket missing, and nothing reaches the association.
     *
     * The endpoint asked for the right to edit any of the station's gear, and no member-facing screen
     * mentioned lost at all. Both halves of that are what this walks.
     */
    test('a member reports their own gear missing and the association is not told', async ({
        clusterStationManagerPage: managerPage, clusterStationMemberPage: memberPage, browser, request,
    }) => {
        const managerHeaders = await apiHeaders(managerPage)
        const memberHeaders = await apiHeaders(memberPage)
        const gear = await clusterGearFor(
            managerPage, managerHeaders, await ownMemberId(memberPage, memberHeaders), 'CLS52A')

        await memberPage.goto('/station/profile/inventory')
        await expect(cardFor(memberPage, gear.internalId)).toBeVisible({timeout: 15000})

        const note = 'Beim Einsatz liegen geblieben'
        await cardFor(memberPage, gear.internalId).getByTestId('item-report-lost').click()
        await memberPage.getByTestId('report-lost-note').fill(note)
        await memberPage.getByTestId('report-lost-submit').click()

        await expect(cardFor(memberPage, gear.internalId).getByTestId('item-lost-note'))
            .toContainText(note, {timeout: 15000})

        // The association hears nothing until somebody asks it for a replacement
        const gearManager = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const clusterView = await clusterPage(browser, request, gearManager)
        const cluster = await theSeededCluster(clusterView)
        const queue = await clusterView.request
            .get('/api/v1/cluster/inventory/queue', {headers: await clusterHeaders(clusterView, cluster)})
            .then(r => r.json())
        expect(queue.some((entry: {itemName: string}) => entry.itemName === gear.internalId)).toBeFalsy()
        await clusterView.context().close()
    })

    /**
     * CLS-52b - The station reports the loss, and the association reads both notes.
     *
     * Two notes with two authors, neither standing in for the other. The manager's is what the station
     * is asking for; the member's is what happened to them.
     */
    test('the station reports the loss and the association reads both notes', async ({
        clusterStationManagerPage: managerPage, clusterStationMemberPage: memberPage, browser, request,
    }) => {
        const managerHeaders = await apiHeaders(managerPage)
        const memberHeaders = await apiHeaders(memberPage)
        const gear = await clusterGearFor(
            managerPage, managerHeaders, await ownMemberId(memberPage, memberHeaders), 'CLS52B')
        await memberPage.request.put(`/api/v1/inventory-items/${gear.id}/lost`, {
            headers: memberHeaders,
            data: {note: 'Aus der Tasche gefallen'},
        })

        const managerNote = `Wir brauchen Ersatz ${Date.now()}`
        await managerPage.goto(`/station/inventory/item/${gear.id}`)
        await expect(managerPage.getByTestId('report-loss')).toBeVisible({timeout: 15000})
        await managerPage.getByTestId('report-loss-open').click()
        await managerPage.getByTestId('report-loss-note').fill(managerNote)
        // Enabled before clicked: a send the form will not accept would otherwise be waited on until
        // the story runs out of time, and the reason would be nowhere in the failure
        await expect(managerPage.getByTestId('report-loss-send')).toBeEnabled()
        await managerPage.getByTestId('report-loss-send').click()
        // The screen says the chain has been started, which is how the story knows the send landed
        await expect(managerPage.getByText('Die Bewegung wurde angestoßen')).toBeVisible({timeout: 15000})

        const gearManager = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const clusterView = await clusterPage(browser, request, gearManager)
        const cluster = await theSeededCluster(clusterView)
        const headers = await clusterHeaders(clusterView, cluster)
        const queue = await clusterView.request
            .get('/api/v1/cluster/inventory/queue', {headers})
            .then(r => r.json())
        const waiting = queue.find((entry: {itemName: string}) => entry.itemName === gear.name)
        expect(waiting, 'the report waits on the association').toBeTruthy()

        await clusterView.goto(`/cluster/inventory/movement/${waiting.movementId}`)
        await expect(clusterView, 'the association opens the movement rather than being sent elsewhere')
            .toHaveURL(new RegExp(`/cluster/inventory/movement/${waiting.movementId}$`))
        await expect(clusterView.getByTestId('loss-report')).toBeVisible({timeout: 15000})
        await expect(clusterView.getByTestId('loss-report-manager-note')).toContainText(managerNote)
        await expect(clusterView.getByTestId('loss-report-member-note')).toContainText('Aus der Tasche gefallen')
        await clusterView.context().close()
    })

    /**
     * CLS-52c - A refused replacement leaves the loss standing.
     *
     * The loss is not the association's to accept or refuse. What it answers is the replacement, and a
     * refusal does not find the jacket.
     */
    test('a refused replacement leaves the item missing', async ({
        clusterStationManagerPage: managerPage, clusterStationMemberPage: memberPage, browser, request,
    }) => {
        const managerHeaders = await apiHeaders(managerPage)
        const memberHeaders = await apiHeaders(memberPage)
        const gear = await clusterGearFor(
            managerPage, managerHeaders, await ownMemberId(memberPage, memberHeaders), 'CLS52C')
        await memberPage.request.put(`/api/v1/inventory-items/${gear.id}/lost`, {
            headers: memberHeaders, data: {note: 'Weg'},
        })
        const reported = await managerPage.request.post(`/api/v1/inventory-items/${gear.id}/loss-report`, {
            headers: managerHeaders,
            multipart: {note: 'Bitte Ersatz'},
        })
        expect(reported.ok(), 'the report is raised').toBeTruthy()
        const movementId = (await reported.json()).id

        // The association refusing is arranged rather than walked: what this story is about is what the
        // station is left looking at afterwards, and CLS-52b already walks the association's side.
        const gearManager = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const clusterView = await clusterPage(browser, request, gearManager)
        const cluster = await theSeededCluster(clusterView)
        const refused = await clusterView.request.post(`/api/v1/movements/${movementId}/decline`, {
            headers: await clusterHeaders(clusterView, cluster),
            data: {reason: 'Kein Ersatz vorhanden'},
        })
        expect(refused.ok(), 'the association may refuse a replacement').toBeTruthy()
        await clusterView.context().close()

        await managerPage.goto(`/station/inventory/item/${gear.id}`)
        await expect(managerPage.getByTestId('app-shell')).toBeVisible({timeout: 15000})
        // A refusal does not find the jacket. The panel that only shows for missing gear is still there,
        // offering the report again, because asking a second time is the station's to decide.
        await expect(managerPage.getByTestId('report-loss')).toBeVisible({timeout: 15000})
        const item = await managerPage.request
            .get(`/api/v1/inventory-items/${gear.id}`, {headers: managerHeaders})
            .then(r => r.json())
        expect(item.custody, 'still missing after the refusal').toBe('LOST')
    })

    /**
     * CLS-52d - The association demands a document before it will replace anything.
     *
     * The report is refused before it is written down, which is the point: a report the association will
     * not look at should never become a movement it has to close.
     */
    test('a report without the document the association demands is refused', async ({
        clusterStationManagerPage: managerPage, clusterStationMemberPage: memberPage, browser, request,
    }) => {
        const managerHeaders = await apiHeaders(managerPage)
        const memberHeaders = await apiHeaders(memberPage)
        const gear = await clusterGearFor(
            managerPage, managerHeaders, await ownMemberId(memberPage, memberHeaders), 'CLS52D')
        await memberPage.request.put(`/api/v1/inventory-items/${gear.id}/lost`, {
            headers: memberHeaders, data: {note: 'Weg'},
        })

        const gearManager = await clusterAccountWith(request, 'CLUSTER_INVENTORY_MANAGER')
        const clusterView = await clusterPage(browser, request, gearManager)
        const cluster = await theSeededCluster(clusterView)
        const clusterApi = await clusterHeaders(clusterView, cluster)
        const asks = async () => (await clusterView.request
            .get('/api/v1/cluster/inventory/loss-report', {headers: clusterApi})
            .then(r => r.json())).requires

        await clusterView.goto('/cluster/inventory/settings')
        await expect(clusterView.getByTestId('loss-report-setting')).toBeVisible({timeout: 15000})
        await clusterView.getByTestId('loss-report-requires').selectOption('DOCUMENT')
        // The select saves as it changes, and the report below is answered by what was saved
        await expect.poll(asks, {timeout: 15000}).toBe('DOCUMENT')

        try {
            const refused = await managerPage.request.post(`/api/v1/inventory-items/${gear.id}/loss-report`, {
                headers: managerHeaders,
                multipart: {note: 'Bitte Ersatz'},
            })
            expect(refused.status(), 'a report without the document is refused').toBe(400)

            // The station's own screen asks for the file rather than letting the report be sent without it
            await managerPage.goto(`/station/inventory/item/${gear.id}`)
            await managerPage.getByTestId('report-loss-open').click({timeout: 15000})
            await managerPage.getByTestId('report-loss-note').fill('Bitte Ersatz')
            await expect(managerPage.getByTestId('report-loss-document')).toBeVisible()
            await expect(managerPage.getByTestId('report-loss-send')).toBeDisabled()
        } finally {
            await clusterView.getByTestId('loss-report-requires').selectOption('NOTHING')
            await expect.poll(asks, {timeout: 15000}).toBe('NOTHING')
            await clusterView.context().close()
        }
    })

    /**
     * CLS-52e - A guardian reports for the person they act for.
     *
     * The same screen, the other tab. The note records the guardian as its author acting for the member,
     * so the trail says who actually wrote it.
     */
    test('a guardian reports a loss for the person they act for', async ({
        clusterStationManagerPage: managerPage, browser, request,
    }) => {
        const managerHeaders = await apiHeaders(managerPage)
        // At the manager's own station: the gear this story makes lives there, and gear at another
        // station is not the guardian's to report however much they act for its holder
        const manager = await clusterStationManager(request)
        const accounts = await demoAccounts(request)
        const guardian = accounts.find(account => !!account.email
            && account.stationId === manager.stationId
            && account.permissions.includes('MEMBER_GUARDIAN'))
        expect(guardian, 'somebody at this station acts for somebody else').toBeTruthy()
        const page = await pageAsThrowaway(browser, request, [], guardian)

        const managed = await page.request
            .get('/api/v1/managed-members', {headers: await apiHeaders(page)})
            .then(r => r.json())
        expect(managed.length, 'the seeded guardian looks after somebody').toBeGreaterThan(0)
        const gear = await clusterGearFor(managerPage, managerHeaders, managed[0].id, 'CLS52E')

        await page.goto('/station/profile/inventory')
        // Their charge's tab, which is where a guardian does everything else for them too
        await page.getByRole('button', {name: managed[0].name}).click({timeout: 15000})
        await expect(cardFor(page, gear.internalId)).toBeVisible({timeout: 15000})

        await cardFor(page, gear.internalId).getByTestId('item-report-lost').click()
        await page.getByTestId('report-lost-note').fill('Im Zeltlager verloren')
        await page.getByTestId('report-lost-submit').click()

        // The note says who wrote it, which is the guardian acting for the member rather than the member
        const note = cardFor(page, gear.internalId).getByTestId('item-lost-note')
        await expect(note).toContainText('Im Zeltlager verloren', {timeout: 15000})
        await expect(note).toContainText(guardian!.lastName)
        await page.context().close()
    })
})
