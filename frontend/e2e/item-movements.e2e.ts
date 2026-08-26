/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders, stationPeers, pageAsThrowaway} from './fixtures/auth'

/**
 * Gear walking between parties, as a chain of steps somebody acknowledges.
 *
 * A movement is not a status that jumps: every step is one a person actually saw, it says who it belongs
 * to, and the record keeps the words it was walked under. These stories walk the chains a station's own
 * gear takes, and the one an owner outside Ember leaves a gap in.
 */

/**
 * A piece of gear to move, in an inventory of the story's own.
 *
 * Its own inventory rather than a seeded one, because a chain is bound to the pair of an inventory and an
 * owner: two stories binding their own chain to the same inventory would each be walking the other's.
 */
async function gear(page: Page, headers: Record<string, string>, label: string, ownerKind = 'STATION') {
    const stamp = `${label}-${Date.now()}-${Math.floor(performance.now())}`
    const inventory = await page.request.post('/api/v1/inventories',
        {headers, data: {name: `Prüfinventar ${stamp}`, inventoryType: 'MIXED', hasSizes: false}})
    expect(inventory.ok(), 'the station keeps an inventory for this story').toBeTruthy()
    const inventoryId = (await inventory.json()).id

    return {inventoryId, item: await addTo(page, headers, inventoryId, stamp, ownerKind)}
}

/** One more piece in an inventory a story already made. */
async function addTo(
    page: Page,
    headers: Record<string, string>,
    inventoryId: number,
    label: string,
    ownerKind = 'STATION',
) {
    const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
        headers,
        data: {internalId: `${label}-${Math.floor(Math.random() * 1e6)}`, name: 'Prüfstück',
            sizeId: null, metadata: null, ownerKind, ownerClusterId: null},
    })
    expect(made.ok(), 'the station records a piece to move').toBeTruthy()
    return made.json()
}

async function someMember(page: Page, headers: Record<string, string>) {
    const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
    const member = (Array.isArray(members) ? members : members.members ?? [])
        .find((m: {userType: string}) => m.userType === 'MEMBER')
    expect(member, 'the station has an ordinary member').toBeTruthy()
    return member
}

async function detail(page: Page, headers: Record<string, string>, id: number) {
    return page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
}

/** Presses whatever step is next until the chain closes or it is somebody else's turn. */
async function walk(page: Page, headers: Record<string, string>, id: number, pick?: () => Promise<number | null>) {
    for (let guard = 8; guard > 0; guard -= 1) {
        const seen = await detail(page, headers, id)
        if (seen.movement.state !== 'OPEN') return seen
        const current = seen.steps.find((s: {current: boolean}) => s.current)
        if (!current?.actionable) return seen
        const pickedItemId = current.picksItem && pick ? await pick() : null
        const stepped = await page.request.post(`/api/v1/movements/${id}/acknowledge`,
            {headers, data: {stepId: current.id, note: '', pickedItemId}})
        if (!stepped.ok()) return detail(page, headers, id)
    }
    return detail(page, headers, id)
}

test.describe('Item movements', () => {
    /**
     * ITM-14 - Gear is handed back to its owner with nothing in return.
     *
     * A return is a chain like any other, and what makes it a return is that nothing arrives to replace
     * what left.
     */
    test('gear handed back brings nothing in return', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'RET', 'CLUSTER')

        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'RETURN', outgoingItemId: item.id, inventoryId, reason: 'Nicht mehr gebraucht'},
        })
        expect(started.ok()).toBeTruthy()
        const id = (await started.json()).movement.id

        const done = await walk(page, headers, id)
        expect(done.movement.state).toBe('DONE')
        expect(done.movement.purpose).toBe('RETURN')
        expect(done.steps.every((s: {picksItem: boolean}) => !s.picksItem),
            'no step names anything arriving').toBeTruthy()

        const after = await page.request
            .get(`/api/v1/inventory-items/${item.id}`, {headers})
            .then(r => r.json())
        expect(after.custody, 'and the piece has left the station').toBe('WITH_OWNER')
    })

    /**
     * ITM-15 - An exchange of station-owned gear is three steps.
     *
     * No owner leg, because there is no owner to wait for: the station has the replacement on its own
     * shelf. This is what the old five-status exchange became.
     */
    test('an exchange of the station\'s own gear is four steps', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'EX3')
        const spare = await addTo(page, headers, inventoryId, 'EX3S')
        const member = await someMember(page, headers)

        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})

        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: item.id, inventoryId,
                reason: 'Zu klein'},
        })
        expect(started.ok()).toBeTruthy()
        const opened = await started.json()
        expect(opened.steps.length, 'four steps, and the member confirms the last of them').toBe(4)
        expect(opened.steps.some((s: {actor: string}) => s.actor === 'OWNER')).toBeFalsy()

        const walked = await walk(page, headers, opened.movement.id, async () => spare.id)
        const waiting = walked.steps.find((step: {current: boolean}) => step.current)
        expect(waiting.actor, 'the last word is the member\'s, so the station stops here').toBe('MEMBER')
        expect((await page.request.post(`/api/v1/movements/${opened.movement.id}/force`,
            {headers, data: {stepId: waiting.id, note: 'Übergabe an der Wache'}})).ok()).toBeTruthy()
        const done = await detail(page, headers, opened.movement.id)
        expect(done.movement.state).toBe('DONE')

        const old = await page.request
            .get(`/api/v1/inventory-items/${item.id}`, {headers})
            .then(r => r.json())
        const replacement = await page.request
            .get(`/api/v1/inventory-items/${spare.id}`, {headers})
            .then(r => r.json())
        expect(old.custody, 'the old one is back in the station\'s store').toBe('WITH_OWNER')
        expect(replacement.custody, 'and the member is wearing the replacement').toBe('WITH_MEMBER')
    })

    /**
     * ITM-16 - An exchange with an owner outside Ember shows its gap.
     *
     * The station walks the owner's steps because nobody else can, and the record says asserted rather
     * than confirmed so the difference survives being read later. The words are the behaviour here.
     */
    test('a station standing in for an absent owner is recorded as asserting', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'GAP', 'CLUSTER')

        const flow = await page.request.post('/api/v1/movement-flows',
            {headers, data: {name: `Rückgabe mit Trägerbein ${Date.now()}`, purpose: 'RETURN'}})
        expect(flow.ok()).toBeTruthy()
        const flowId = (await flow.json()).id

        for (const step of [
            {label: 'Wache schickt zurück', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'IN_TRANSIT', picksItem: false},
            {label: 'Träger nimmt an', actor: 'OWNER', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        ]) {
            expect((await page.request.post(`/api/v1/movement-flows/${flowId}/steps`,
                {headers, data: step})).ok()).toBeTruthy()
        }
        expect((await page.request.put('/api/v1/movement-flow-bindings', {
            headers,
            data: {inventoryId, ownerKind: 'CLUSTER', purpose: 'RETURN', flowId},
        })).ok()).toBeTruthy()

        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'RETURN', outgoingItemId: item.id, inventoryId, reason: 'Zurück'},
        })
        expect(started.ok()).toBeTruthy()

        const done = await walk(page, headers, (await started.json()).movement.id)
        expect(done.movement.state).toBe('DONE')

        const owner = done.steps.filter((s: {actor: string}) => s.actor === 'OWNER')
        const own = done.steps.filter((s: {actor: string}) => s.actor === 'STATION')
        expect(owner.length, 'the chain had a step for the owner').toBeGreaterThan(0)
        expect(owner.every((s: {ackKind: string}) => s.ackKind === 'ASSERTED'),
            'the station stood in, and the record says so').toBeTruthy()
        expect(own.every((s: {ackKind: string}) => s.ackKind === 'CONFIRMED'),
            'while what it saw itself reads as confirmed').toBeTruthy()
    })

    /**
     * ITM-17 - The member follows their own exchange.
     *
     * The chain is readable from the member's side with the current step named, and the steps that are
     * not theirs carry nothing to press.
     */
    test('a member follows their own exchange without being able to press it',
        async ({managerPage: page, browser, request}) => {
            const headers = await apiHeaders(page)
            const {inventoryId, item} = await gear(page, headers, 'FOLLOW')
            const {member} = await stationPeers(request)

            const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
            const row = (Array.isArray(members) ? members : members.members ?? [])
                .find((m: {email: string}) => m.email === member.email)
            expect(row, 'the member the fixture signs in as is at this station').toBeTruthy()

            await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
                {headers, data: {memberId: row.id, memberName: null}})
            const started = await page.request.post('/api/v1/movements', {
                headers,
                data: {purpose: 'EXCHANGE', memberId: row.id, outgoingItemId: item.id, inventoryId,
                    reason: 'Zu klein'},
            })
            expect(started.ok()).toBeTruthy()
            const id = (await started.json()).movement.id

            const theirs = await pageAsThrowaway(browser, request, [], member)
            const theirHeaders = await apiHeaders(theirs)
            const seen = await detail(theirs, theirHeaders, id)

            expect(seen.movement.currentStepLabel, 'the step it is standing on is named').toBeTruthy()
            const notTheirs = seen.steps.filter((s: {actor: string}) => s.actor !== 'MEMBER')
            expect(notTheirs.every((s: {actionable: boolean}) => !s.actionable),
                'and nothing that is not theirs offers a button').toBeTruthy()

            await theirs.goto('/station/profile/inventory')
            await expect(theirs.getByTestId('app-shell')).toBeVisible()
            await theirs.context().close()
        })

    /**
     * ITM-18 - A declined movement puts the item back.
     *
     * Somebody with no replacement in stock has to be able to say so, and the member is left holding what
     * they started with rather than nothing.
     */
    test('declining puts the member\'s own gear back', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'DECL')
        const member = await someMember(page, headers)

        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: item.id, inventoryId,
                reason: 'Zu klein'},
        })
        const id = (await started.json()).movement.id

        const declined = await page.request.post(`/api/v1/movements/${id}/decline`,
            {headers, data: {reason: 'Kein Ersatz da'}})
        expect(declined.ok()).toBeTruthy()

        const closed = await detail(page, headers, id)
        expect(closed.movement.state).toBe('DECLINED')
        expect(closed.movement.closeReason, 'and says why').toContain('Kein Ersatz da')

        const back = await page.request
            .get(`/api/v1/inventory-items/${item.id}`, {headers})
            .then(r => r.json())
        expect(back.custody, 'the member has their own piece again').toBe('WITH_MEMBER')
        expect(back.assignedTo).toBe(member.id)
    })

    /**
     * ITM-19 - A movement can be cancelled while it is still ours.
     *
     * Taking back a request nobody has acted on leaves no trace on the gear, because the gear never went
     * anywhere.
     */
    test('cancelling leaves the gear where it never left', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'CANC')
        const member = await someMember(page, headers)

        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: item.id, inventoryId,
                reason: 'Doch nicht'},
        })
        const id = (await started.json()).movement.id

        const cancelled = await page.request.post(`/api/v1/movements/${id}/cancel`,
            {headers, data: {reason: 'Passt doch'}})
        expect(cancelled.ok()).toBeTruthy()

        const closed = await detail(page, headers, id)
        expect(closed.movement.state).toBe('CANCELLED')

        const untouched = await page.request
            .get(`/api/v1/inventory-items/${item.id}`, {headers})
            .then(r => r.json())
        expect(untouched.custody, 'the piece never left the member').toBe('WITH_MEMBER')
    })

    /**
     * ITM-20 - A step nobody answers can be forced, and says so afterwards.
     *
     * Forcing needs a note, because the record has to say who decided it and why, and the step reads as
     * forced for good rather than quietly becoming a confirmation.
     */
    test('a step can be forced, with a note, and reads as forced afterwards', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'FORCE', 'CLUSTER')
        const member = await someMember(page, headers)

        // A chain whose second step is the member's, because forcing is for a step that belongs to
        // somebody else and has gone unanswered. A station's own step is simply pressed.
        const flow = await page.request.post('/api/v1/movement-flows',
            {headers, data: {name: `Warten auf das Mitglied ${Date.now()}`, purpose: 'RETURN'}})
        const flowId = (await flow.json()).id
        for (const step of [
            {label: 'Wache kündigt an', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'WITH_MEMBER', picksItem: false},
            {label: 'Mitglied gibt ab', actor: 'MEMBER', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        ]) {
            expect((await page.request.post(`/api/v1/movement-flows/${flowId}/steps`,
                {headers, data: step})).ok()).toBeTruthy()
        }
        expect((await page.request.put('/api/v1/movement-flow-bindings', {
            headers,
            data: {inventoryId, ownerKind: 'CLUSTER', purpose: 'RETURN', party: 'MEMBER', flowId},
        })).ok()).toBeTruthy()

        await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
            {headers, data: {memberId: member.id, memberName: null}})
        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'RETURN', memberId: member.id, outgoingItemId: item.id, inventoryId,
                reason: 'Bitte abgeben'},
        })
        expect(started.ok()).toBeTruthy()
        const id = (await started.json()).movement.id
        const current = (await detail(page, headers, id)).steps.find((s: {current: boolean}) => s.current)
        expect(current.actor, 'the chain is waiting on the member').toBe('MEMBER')

        const withoutNote = await page.request.post(`/api/v1/movements/${id}/force`,
            {headers, data: {stepId: current.id, note: ''}})
        expect(withoutNote.ok(), 'forcing without saying why is refused').toBeFalsy()

        const forced = await page.request.post(`/api/v1/movements/${id}/force`,
            {headers, data: {stepId: current.id, note: 'Mitglied nicht erreichbar'}})
        expect(forced.ok()).toBeTruthy()

        const seen = await detail(page, headers, id)
        const stamped = seen.steps.find((s: {id: number}) => s.id === current.id)
        expect(stamped.ackKind, 'and it reads as forced rather than confirmed').toBe('FORCED')
        expect(stamped.note).toContain('Mitglied nicht erreichbar')
    })

    /**
     * ITM-21 - A finished movement keeps the words it was walked under.
     *
     * Renaming a step changes what the next movement says, never what a finished one said. The record is
     * of what happened, not of what the flow is called today. A chain asks and then confirms, so the step
     * being renamed needs a receipt behind it before the chain is usable at all.
     */
    test('renaming a step leaves a finished movement reading as it was walked', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {inventoryId, item} = await gear(page, headers, 'WORDS', 'CLUSTER')

        const flow = await page.request.post('/api/v1/movement-flows',
            {headers, data: {name: `Wortlaut ${Date.now()}`, purpose: 'RETURN'}})
        const flowId = (await flow.json()).id
        const step = await page.request.post(`/api/v1/movement-flows/${flowId}/steps`, {
            headers,
            data: {label: 'Alte Worte', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        })
        expect(step.ok()).toBeTruthy()
        const stepId = (await step.json()).id
        expect((await page.request.post(`/api/v1/movement-flows/${flowId}/steps`, {
            headers,
            data: {label: 'Träger bestätigt', actor: 'OWNER', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        })).ok()).toBeTruthy()
        expect((await page.request.put('/api/v1/movement-flow-bindings', {
            headers,
            data: {inventoryId, ownerKind: 'CLUSTER', purpose: 'RETURN', flowId},
        })).ok()).toBeTruthy()

        const started = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'RETURN', outgoingItemId: item.id, inventoryId, reason: 'Zurück'},
        })
        const id = (await started.json()).movement.id
        const done = await walk(page, headers, id)
        expect(done.movement.state).toBe('DONE')

        const renamed = await page.request.put(`/api/v1/movement-flow-steps/${stepId}`, {
            headers,
            data: {label: 'Neue Worte', actor: 'STATION', subject: 'OUTGOING',
                custodyAfter: 'WITH_OWNER', picksItem: false},
        })
        expect(renamed.ok()).toBeTruthy()

        const walked = JSON.stringify(await detail(page, headers, id))
        expect(walked, 'the finished movement still reads with the words it was walked under')
            .toContain('Alte Worte')
        expect(walked, 'and not with the ones it never saw').not.toContain('Neue Worte')

        // A movement started afterwards walks the new wording, which is what renaming is for
        const next = await addTo(page, headers, inventoryId, 'WORDS2', 'CLUSTER')
        const later = await page.request.post('/api/v1/movements', {
            headers,
            data: {purpose: 'RETURN', outgoingItemId: next.id, inventoryId, reason: 'Zurück'},
        })
        expect(JSON.stringify(await later.json())).toContain('Neue Worte')
    })
})
