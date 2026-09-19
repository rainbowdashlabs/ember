/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'
import {MovementFilterColumn, setMovementFilter} from './fixtures/movementFilter'
import {movementRow} from './fixtures/movementRow'

/**
 * Where a movement says it stands, and what may move it.
 *
 * A movement never announces a status: where it stands is the step it is on, read off where the two
 * pieces are. That makes it easy for something that touched neither of them to look like a step
 * forward, which is what these stories hold the line on. Nothing but walking the chain moves a
 * movement, one that was called off says so rather than passing for a finished one, and putting one
 * right is putting the pieces right.
 */

/** A piece of the station's own gear in an inventory of this story's own, already on a member. */
async function swapOnItsWay(page: Page, headers: Record<string, string>, label: string) {
    const stamp = `${label}-${Date.now()}-${Math.floor(performance.now())}`
    const inventoryName = `Vorgangsinventar ${stamp}`
    const inventory = await page.request.post('/api/v1/inventories',
        {headers, data: {name: inventoryName, inventoryType: 'MIXED', hasSizes: false}})
    expect(inventory.ok(), 'the station keeps an inventory for this story').toBeTruthy()
    const inventoryId = (await inventory.json()).id

    const made = await page.request.post(`/api/v1/inventories/${inventoryId}/items`, {
        headers,
        data: {internalId: stamp, name: 'Prüfstück', sizeId: null, metadata: null,
            ownerKind: 'STATION', ownerClusterId: null},
    })
    expect(made.ok(), 'the station records a piece to swap').toBeTruthy()
    const item = await made.json()

    const members = await page.request.get('/api/v1/station-members', {headers}).then(r => r.json())
    const member = (Array.isArray(members) ? members : members.members ?? [])
        .find((m: {userType: string}) => m.userType === 'MEMBER')
    expect(member, 'the station has an ordinary member').toBeTruthy()

    await page.request.put(`/api/v1/inventory-items/${item.id}/assign`,
        {headers, data: {memberId: member.id, memberName: null}})

    const raised = await page.request.post('/api/v1/movements', {
        headers,
        data: {purpose: 'EXCHANGE', memberId: member.id, outgoingItemId: item.id, inventoryId,
            oldSizeId: null, newSizeId: null, reason: 'Passt nicht mehr'},
    })
    expect(raised.ok(), await raised.text()).toBeTruthy()
    return {id: (await raised.json()).movement.id, inventoryName, itemId: item.id, memberId: member.id}
}

/**
 * What the station's queue says about one movement, asked of the API rather than the screen.
 *
 * <p>Two labels, because a step is named after the state it brings about: `reached` is what is true
 * of the world now and `pending` is what pressing the row's button would make true.
 */
async function standingOf(page: Page, headers: Record<string, string>, id: number) {
    const rows = await page.request.get('/api/v1/movements', {headers}).then(r => r.json())
    const row = rows.find((entry: {id: number}) => entry.id === id)
    return {state: row?.state, reached: row?.reachedStepLabel, pending: row?.currentStepLabel}
}

/** Every step somebody has answered so far, which is what an idle panel must not add to. */
async function answered(page: Page, headers: Record<string, string>, id: number) {
    const detail = await page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
    return detail.steps.filter((step: {acknowledgedAt: string | null}) => step.acknowledgedAt !== null)
}

/** Where a piece is, which is what a movement's standing is read off. */
async function custodyOf(page: Page, headers: Record<string, string>, itemId: number) {
    const item = await page.request.get(`/api/v1/inventory-items/${itemId}`, {headers}).then(r => r.json())
    return item.custody
}

test.describe('Movement queue', () => {
    /**
     * MVQ-1 - Opening the step a movement stands on and closing it again leaves it exactly where it stood.
     *
     * The panel that walks a movement on is a form, and closing it has been mistaken for an answer to
     * the step. The story watches the wire as well as the screen: not one request that could change
     * anything may leave while the panel is opened and closed.
     */
    test('opening a movement and closing it again changes nothing', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {id, inventoryName} = await swapOnItsWay(page, headers, 'OPEN')

        const before = await standingOf(page, headers, id)
        expect(before.state, 'a fresh movement is open').toBe('OPEN')
        const stepsBefore = await answered(page, headers, id)

        await page.goto('/station/inventory/movements')
        const row = movementRow(page, id)
        await expect(row).toBeVisible({timeout: 15000})
        await expect(row.getByTestId('movement-step'),
            'the row says what is true of the world, not what is being waited on').toHaveText(before.reached)
        await expect(row.getByTestId('movement-acknowledge'),
            'and the button says what pressing it would make true').toHaveText(before.pending)

        const changing: string[] = []
        page.on('request', request => {
            if (request.method() === 'GET') return
            if (/\/api\/v1\/movements/.test(request.url())) changing.push(`${request.method()} ${request.url()}`)
        })

        await row.getByTestId('movement-acknowledge').click()
        await expect(page.getByTestId('movement-ack-modal')).toBeVisible()
        await page.getByTestId('modal').getByRole('button', {name: 'Schließen'}).click()
        await expect(page.getByTestId('movement-ack-modal')).toBeHidden()

        expect(changing, 'closing the panel asks the server for nothing').toEqual([])
        await expect(row.getByTestId('movement-step')).toHaveText(before.reached)

        const after = await standingOf(page, headers, id)
        expect(after, 'and the movement stands where it stood').toEqual(before)
        expect((await answered(page, headers, id)).length, 'with nothing added to its history')
            .toBe(stepsBefore.length)
    })

    /**
     * MVQ-2 - A movement that was called off does not pass for a finished one.
     *
     * Calling one off stops it; it does not complete it. Reading every stopped movement as finished
     * made pressing the button that calls one off look exactly like pressing the one that completes
     * it, and the row moved from the beginning of its chain to the end of it.
     */
    test('a movement called off reads as called off, not as finished', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const {id} = await swapOnItsWay(page, headers, 'CALLOFF')

        const calledOff = await page.request.post(`/api/v1/movements/${id}/cancel`,
            {headers, data: {reason: 'Passt doch'}})
        expect(calledOff.ok(), 'the station calls the movement off').toBeTruthy()

        expect((await standingOf(page, headers, id)).state, 'which is not the same as finishing it')
            .toBe('CANCELLED')

        await page.goto('/station/inventory/movements')
        await setMovementFilter(page, MovementFilterColumn.STEP, [])
        const row = movementRow(page, id)
        await expect(row, 'which the queue shows once the ticks stop keeping it to what is still running')
            .toBeVisible({timeout: 15000})
        await expect(row.getByTestId('movement-state')).toHaveText('Abgebrochen')
        await expect(row.getByTestId('movement-acknowledge'),
            'and there is nothing left to walk it on with').toHaveCount(0)
    })

    /**
     * MVQ-3 - A movement standing further along than it should is put right by hand.
     *
     * There is no status to overwrite: where it stands is read off where the two pieces are, so
     * putting one right is saying where the pieces are. The reason is not optional, and the history
     * says afterwards that a person set this rather than that anybody walked it.
     */
    test('a movement that stands too far is put right, with the reason on record',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const {id, itemId} = await swapOnItsWay(page, headers, 'BACK')

            const opening = await page.request.get(`/api/v1/movements/${id}`, {headers}).then(r => r.json())
            const first = opening.steps.find((step: {current: boolean}) => step.current)
            const stepped = await page.request.post(`/api/v1/movements/${id}/acknowledge`,
                {headers, data: {stepId: first.id, note: 'Abgegeben'}})
            expect(stepped.ok(), await stepped.text()).toBeTruthy()

            const tooFar = await standingOf(page, headers, id)
            expect(tooFar.reached, 'the movement has moved on a step').toBe(first.label)
            expect(await custodyOf(page, headers, itemId), 'and the piece went with it')
                .not.toBe('WITH_MEMBER')

            await page.goto('/station/inventory/movements')
            const row = movementRow(page, id)
            await expect(row).toBeVisible({timeout: 15000})

            await row.getByTestId('movement-correct').click()
            await expect(page.getByTestId('movement-correct-modal')).toBeVisible()
            await page.getByTestId('correct-outgoing').selectOption('WITH_MEMBER')
            await page.getByTestId('correct-reason').fill('Wurde nie abgegeben')
            await page.getByTestId('correct-confirm').click()

            const opened = opening.steps[opening.steps.indexOf(first) - 1]
            await expect(row.getByTestId('movement-step'),
                'the row is back to the state the pieces are actually in').toHaveText(opened.label)
            expect((await standingOf(page, headers, id)).pending, 'and the step it waits on is the one it lost')
                .toBe(first.label)
            expect(await custodyOf(page, headers, itemId),
                'because saying where the piece is is what put it back').toBe('WITH_MEMBER')
        })

    /**
     * MVQ-4 - A movement of any kind is started from the queue, and refused where no chain serves it.
     *
     * The wizard is the one way in, so it is the one place where a station finds out that a
     * combination it asked for has no chain behind it. It has to say so rather than fail on submit.
     */
    test('the wizard starts a hand-out and draws the chain before it writes anything',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const stamp = `WIZ-${Date.now()}`
            const inventory = await page.request.post('/api/v1/inventories',
                {headers, data: {name: `Wizardinventar ${stamp}`, inventoryType: 'EXTERNAL', hasSizes: false}})
            expect(inventory.ok(), await inventory.text()).toBeTruthy()

            const about = (rows: {inventoryName: string; purpose?: string}[]) =>
                rows.filter(row => row.inventoryName === `Wizardinventar ${stamp}`)

            await page.goto('/station/inventory/movements')
            await page.getByTestId('movement-create').click()
            await expect(page.getByTestId('movement-wizard')).toBeVisible()

            await page.getByTestId('wizard-purpose-ISSUE').click()
            await page.getByTestId('wizard-next').click()
            await page.getByTestId('wizard-party-store').click()
            await page.getByTestId('wizard-next').click()
            const picker = page.getByTestId('wizard-inventory')
            await picker.getByRole('searchbox').fill(`Wizardinventar ${stamp}`)
            const offered = picker.getByRole('option')
            await expect(offered, 'the search leaves the one inventory standing').toHaveCount(1)
            await offered.click()
            await page.getByTestId('wizard-next').click()
            await page.getByTestId('wizard-reason').fill('Für das Lager bestellt')
            await page.getByTestId('wizard-next').click()

            await expect(page.getByTestId('wizard-preview-steps'),
                'the chain is drawn before anything is written').toBeVisible()
            expect(about(await page.request.get('/api/v1/movements', {headers}).then(r => r.json())),
                'and nothing has been written yet').toEqual([])

            await page.getByTestId('wizard-start').click()
            await expect(page.getByTestId('movement-wizard')).toBeHidden()

            await expect(async () => {
                const rows = await page.request.get('/api/v1/movements', {headers}).then(r => r.json())
                expect(about(rows).map(row => row.purpose),
                    'and the hand-out is now under way').toEqual(['ISSUE'])
            }).toPass()
        })
})
