/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Browser, Page} from '@playwright/test'
import {test, expect, apiHeaders, demoAccounts, pageAsThrowaway, stationPeers} from './fixtures/auth'

/**
 * A member answering for their own gear, and a checker reading what they said.
 *
 * <p>The point of the feature is that nobody has to be in the room, so the stories put the member
 * and the checker on separate pages throughout: a checker who could sign off their own words would
 * leave one name on a record that exists to carry two.
 *
 * <p>Each story asks somebody nobody has asked yet, in a session of its own. A member holding an
 * unfinished task is passed over rather than asked twice, and the one act that would end such a task
 * is the checker's own walk, which takes the lock the rest of the suite is walking members under.
 * Finding a free member costs a login and disturbs nothing.
 */

/** Which member the signed-in page is, asked of the application rather than assumed. */
async function ownMemberId(page: Page): Promise<number> {
    const session = await page.request.get('/api/v1/session', {headers: await apiHeaders(page)})
    expect(session.ok(), `the session is readable (${await session.text()})`).toBeTruthy()
    return (await session.json()).member.id
}

/** A member of the checker's station holding no task yet, signed in, with the task they were handed. */
interface Asked {
    page: Page
    memberId: number
    taskId: number
}

/**
 * Asks the first member of the station who is free to be asked.
 *
 * <p>Being passed over is the endpoint's own answer to a member who already holds an unfinished
 * task, so an empty answer is read as "try somebody else" rather than as a failure.
 */
async function askSomebody(browser: Browser, request: APIRequestContext, manager: Page): Promise<Asked> {
    const {manager: checker} = await stationPeers(request)
    const candidates = (await demoAccounts(request)).filter(account =>
        !!account.email
        && account.stationId === checker.stationId
        && account.userType === 'MEMBER'
        && account.email !== checker.email)
    const headers = await apiHeaders(manager)
    for (const account of candidates) {
        const page = await pageAsThrowaway(browser, request, [], account)
        const memberId = await ownMemberId(page)
        const response = await manager.request.post('/api/v1/self-checks', {
            headers,
            data: {memberIds: [memberId], dueOn: null},
        })
        expect(response.ok(), `the checker handed a task out (${await response.text()})`).toBeTruthy()
        const handed = await response.json()
        if (handed.length > 0) return {page, memberId, taskId: handed[0].id}
        await page.context().close()
    }
    throw new Error('every member of the station already holds an unfinished self-check')
}

/** Says everything the screen asks, and answers with how many things that was. */
async function answerEverything(member: Page): Promise<number> {
    await expect(member.getByTestId('self-check-submit')).toBeVisible()
    const held = member.locator('[data-testid^="self-check-answer-"][data-testid$="-HAVE_IT"]')
    const gaps = member.locator('[data-testid^="self-check-answer-"][data-testid$="-NEVER_HAD"]')
    const pieces = await held.count()
    const places = await gaps.count()
    for (let index = 0; index < pieces; index++) await held.nth(index).click()
    for (let index = 0; index < places; index++) await gaps.nth(index).click()
    return pieces + places
}

/**
 * Presses the button that takes one answer and waits for the answer to the press.
 *
 * <p>Waiting for the call rather than only for the screen is what tells a refused call apart from a
 * button that did nothing: the two look the same from outside and are entirely different problems.
 * The row itself is not asserted on here, because taking the last outstanding answer of a task that
 * also holds a refused one sends the whole task back and clears what was taken, so the row it was
 * about is gone by the time the screen redraws.
 */
async function takeRow(manager: Page, taskId: number, rowId: number): Promise<void> {
    const [response] = await Promise.all([
        manager.waitForResponse(answer => answer.url().includes(`/self-check-reviews/${taskId}/rows/${rowId}/take`)),
        manager.getByTestId(`review-take-${rowId}`).click(),
    ])
    expect(response.ok(), `taking one answer is accepted (${response.status()} ${await response.text()})`).toBeTruthy()
}

/** One kind of gear the member is asked about, as the task itself describes it. */
interface Required {
    inventoryId: number
    inventoryName: string
    hasSizes: boolean
    homogeneous: boolean
    sizes: {id: number; label: string}[]
    requiredQuantity: number
    assignedQuantity: number
}

interface Assigned {
    id: number
    inventoryId: number
    sizeId?: number | null
    ownerKind: string
    custody: string
}

/** The task as the person answering it reads it, which is the only view that carries their own gear. */
async function ownTask(member: Page, taskId: number): Promise<{required: Required[]; assigned: Assigned[]}> {
    const response = await member.request.get(`/api/v1/self-checks/${taskId}`, {headers: await apiHeaders(member)})
    expect(response.ok(), `the member reads their own task (${await response.text()})`).toBeTruthy()
    return response.json()
}

/**
 * A kind of gear that comes in sizes, holds one thing in many copies, and the member holds a piece
 * of. This is only used to make a gap: what the stories then act on they find on the screen.
 */
function sizedKindTheMemberHolds(task: {required: Required[]; assigned: Assigned[]}): {req: Required; piece: Assigned} {
    for (const req of task.required) {
        if (!req.hasSizes || !req.homogeneous || req.sizes.length === 0) continue
        const piece = task.assigned.find(item =>
            item.inventoryId === req.inventoryId
            && item.sizeId != null
            && item.ownerKind !== 'PARTNER_STATION'
            && item.custody !== 'LOST')
        if (piece) return {req, piece}
    }
    throw new Error('the station keeps no gear in sizes that a member holds a piece of')
}

/**
 * The entry on the screen that offers a given control, named by the key the page hangs it on.
 *
 * <p>Found in the page rather than worked out from the records first. What a member holds is the
 * whole station's business, and a story running beside this one may hand a piece out or take one
 * back between reading the records and clicking: asking the very screen that is about to be clicked
 * leaves no window between the two at all.
 *
 * @param prefix the test id up to the entry's own key, ending in a hyphen
 */
async function entryOffering(member: Page, prefix: string): Promise<string> {
    const offered = member.locator(`[data-testid^="${prefix}"]`).first()
    await expect(offered, `the screen offers ${prefix} on something`).toBeVisible()
    return (await offered.getAttribute('data-testid'))!.slice(prefix.length)
}

/**
 * The entry on the screen that offers both of two controls, named by the key the page hangs it on.
 *
 * <p>A story acting on two controls at once cannot pick an entry by either alone: the size a member
 * puts right is offered on any gear that comes in sizes, and a swap only on gear the station keeps
 * in many copies, so the first entry offering one may well not offer the other.
 */
async function entryOfferingBoth(member: Page, first: string, second: string): Promise<string> {
    const offered = member.locator(`[data-testid^="${first}"]`)
    await expect(offered.first(), `the screen offers ${first} on something`).toBeVisible()
    for (let index = 0; index < (await offered.count()); index++) {
        const key = (await offered.nth(index).getAttribute('data-testid'))!.slice(first.length)
        if ((await member.getByTestId(`${second}${key}`).count()) > 0) return key
    }
    throw new Error(`no entry offers both ${first} and ${second}`)
}

/** The piece an entry key names, for a key of the form {@code piece-<id>}. */
function pieceOf(key: string): number {
    return Number(key)
}

/** The inventory an empty place belongs to, for a key of the form {@code place-<inventory>-<slot>}. */
function inventoryOf(placeKey: string): number {
    return Number(placeKey.split('-')[0])
}

/** The task as the review endpoint reads it, which is where the settlement of each answer stands. */
async function review(manager: Page, taskId: number) {
    const response = await manager.request.get(`/api/v1/self-check-reviews/${taskId}`, {
        headers: await apiHeaders(manager),
    })
    expect(response.ok(), `the checker can read the submission (${await response.text()})`).toBeTruthy()
    return response.json()
}

test.describe('Self-check', () => {
    test.describe.configure({mode: 'serial'})

    /**
     * SFC-1: a member is handed a task, says what they have, hands it in and sees it as handed in.
     */
    test('a member answers for their own gear and hands it in', async ({browser, request, managerPage}) => {
        const {page: memberPage, taskId} = await askSomebody(browser, request, managerPage)

        await memberPage.goto(`/station/inventory/self-check/${taskId}`)
        await expect(memberPage.getByTestId('app-shell')).toBeVisible()

        const asked = await answerEverything(memberPage)
        expect(asked, 'the member is asked about something').toBeGreaterThan(0)

        await memberPage.getByTestId('self-check-save').click()
        await expect(memberPage.getByText('Gespeichert. Du kannst später weitermachen.')).toBeVisible()

        await memberPage.getByTestId('self-check-submit').click()
        await expect(memberPage.getByTestId('self-check-submitted')).toBeVisible()

        const submitted = await review(managerPage, taskId)
        expect(submitted.task.state, 'the task is waiting to be read').toBe('SUBMITTED')
        expect(submitted.submittedByName.length, 'the submission names who entered it').toBeGreaterThan(0)
        await memberPage.context().close()
    })

    /**
     * SFC-2: the checker reads the submission line by line, sends one answer back and takes the
     * rest, and the check that follows names both the person who reported it and the person who
     * signed it off.
     */
    test('a checker settles a submission line by line and the check names both people',
        async ({browser, request, managerPage}) => {
            const {page: memberPage, memberId, taskId} = await askSomebody(browser, request, managerPage)

            await memberPage.goto(`/station/inventory/self-check/${taskId}`)
            await expect(memberPage.getByTestId('app-shell')).toBeVisible()
            await answerEverything(memberPage)
            await memberPage.getByTestId('self-check-submit').click()
            await expect(memberPage.getByTestId('self-check-submitted')).toBeVisible()

            const submitted = await review(managerPage, taskId)
            const rows = submitted.rows.map((entry: {row: {id: number}}) => entry.row.id)
            expect(rows.length, 'the submission carries answers to settle').toBeGreaterThan(1)

            await managerPage.goto(`/station/inventory/checks/self/${taskId}`)
            await expect(managerPage.getByTestId('app-shell')).toBeVisible()
            await expect(managerPage.getByTestId('review-people')).toBeVisible()

            await managerPage.getByTestId(`review-refuse-${rows[0]}`).click()
            await managerPage.getByTestId('review-refuse-reason').fill('Bitte noch einmal im Spind nachsehen.')
            await managerPage.getByTestId('review-refuse-confirm').click()
            await expect(managerPage.getByTestId('review-refuse-reason')).toBeHidden()
            await expect(managerPage.getByTestId(`review-row-${rows[0]}`)).toContainText('Zurückgegeben')

            const midway = await review(managerPage, taskId)
            expect(midway.task.state, 'a task still holding answers stays where it is').toBe('SUBMITTED')

            for (const rowId of rows.slice(1)) await takeRow(managerPage, taskId, rowId)

            const returned = await review(managerPage, taskId)
            expect(returned.task.state, 'a refused answer sends the task back to the member').toBe('OPEN')
            expect(returned.rows.length, 'and it comes back holding only what was refused').toBe(1)
            await expect(managerPage.getByTestId(`review-row-${rows[0]}`)).toContainText('Zurückgegeben')
            await expect(managerPage.locator('[data-testid^="review-row-"]')).toHaveCount(1)

            await memberPage.goto(`/station/inventory/self-check/${taskId}`)
            const cameBack = memberPage.locator('[data-testid^="self-check-refused-"]')
            await expect(cameBack.first()).toContainText('Bitte noch einmal im Spind nachsehen.')

            await answerEverything(memberPage)
            await memberPage.getByTestId('self-check-submit').click()
            await expect(memberPage.getByTestId('self-check-submitted')).toBeVisible()

            const again = await review(managerPage, taskId)
            await managerPage.goto(`/station/inventory/checks/self/${taskId}`)
            await expect(managerPage.getByTestId('review-people')).toBeVisible()
            for (const entry of again.rows) {
                if (entry.row.state !== 'OUTSTANDING') continue
                await takeRow(managerPage, taskId, entry.row.id)
            }
            await expect(managerPage.locator('[data-testid^="review-row-"]').first()).toContainText('Übernommen')

            const done = await review(managerPage, taskId)
            expect(done.task.state, 'the last answer taken finishes the task').toBe('DONE')
            expect(done.task.checkId, 'a finished task writes a real check').toBeTruthy()

            const check = await managerPage.request.get(`/api/v1/inventory-checks/${memberId}/last`, {
                headers: await apiHeaders(managerPage),
            })
            expect(check.ok(), `the member's last check is readable (${await check.text()})`).toBeTruthy()
            const detail = await check.json()
            expect(detail.check.reportedBy, 'the check names who reported it').toBeTruthy()
            expect(detail.reporterFirstName.length, 'the check names the reporter').toBeGreaterThan(0)
            expect(detail.checkerFirstName.length, 'the check names who signed it off').toBeGreaterThan(0)
            expect(detail.check.checkedBy).not.toBe(detail.check.reportedBy)
            await memberPage.context().close()
        })

    /**
     * SFC-4: a member holding something nobody wrote down says which size it is, the checker reads
     * that size on the submission, and putting the record right writes it onto the piece.
     *
     * <p>The place is made rather than looked for: the demo hands every member everything their role
     * asks of them, so taking one piece back off the record is what leaves the empty place this
     * story is about.
     */
    test('a size given for a piece nobody wrote down reaches the record', async ({browser, request, managerPage}) => {
        const {page: memberPage, memberId, taskId} = await askSomebody(browser, request, managerPage)
        const headers = await apiHeaders(managerPage)

        const {piece} = sizedKindTheMemberHolds(await ownTask(memberPage, taskId))
        const emptied = await managerPage.request.put(`/api/v1/inventory-items/${piece.id}/assign`, {
            headers,
            data: {memberId: null},
        })
        expect(emptied.ok(), `the checker takes the piece back off the record (${await emptied.text()})`).toBeTruthy()

        await memberPage.goto(`/station/inventory/self-check/${taskId}`)
        await expect(memberPage.getByTestId('app-shell')).toBeVisible()
        await answerEverything(memberPage)

        const places = memberPage.locator('[data-testid^="self-check-answer-place-"][data-testid$="-HAVE_ONE"]')
        const openPlaces = await places.count()
        expect(openPlaces, 'the member has a place they can be holding something for').toBeGreaterThan(0)
        for (let index = 0; index < openPlaces; index++) await places.nth(index).click()

        const placeKey = await entryOffering(memberPage, 'self-check-size-place-')
        const inventoryId = inventoryOf(placeKey)
        const wanted = (await ownTask(memberPage, taskId)).required.find(req => req.inventoryId === inventoryId)!
            .sizes[0]!

        await memberPage.getByTestId(`self-check-size-place-${placeKey}`).selectOption(String(wanted.id))
        await memberPage.getByTestId('self-check-submit').click()
        await expect(memberPage.getByTestId('self-check-submitted')).toBeVisible()

        const submitted = await review(managerPage, taskId)
        const held = submitted.rows.find((entry: {row: {sizeId: number | null}}) => entry.row.sizeId === wanted.id)
        expect(held, 'the submission carries the answer about the empty place').toBeTruthy()
        expect(held.statedSize, 'spelled the way the inventory spells it').toBe(wanted.label)

        await managerPage.goto(`/station/inventory/checks/self/${taskId}`)
        await expect(managerPage.getByTestId('review-people')).toBeVisible()
        await expect(managerPage.getByTestId(`review-stated-size-${held.row.id}`)).toContainText(wanted.label)

        await managerPage.getByTestId(`review-correct-${held.row.id}`).click()
        const source = managerPage.getByTestId('correct-source')
        if (await source.isVisible()) await source.selectOption('NEW')
        await managerPage.getByTestId('correct-confirm').click()
        await expect(managerPage.getByTestId('correct-confirm')).toBeHidden()

        const settled = await review(managerPage, taskId)
        const named = settled.rows.find((entry: {row: {id: number}}) => entry.row.id === held.row.id)
        expect(named.row.itemId, 'the answer now names a real piece').toBeTruthy()
        expect(named.item.sizeId, 'and that piece carries the size the member gave').toBe(wanted.id)
        expect(named.item.assignedTo, 'against the member it was about').toBe(memberId)
        await memberPage.context().close()
    })

    /**
     * SFC-6: the record has the wrong size against a piece the member is holding. They put the size
     * right on the piece itself, and the checker takes it over onto the record.
     */
    test('a size the record got wrong is put right from the piece itself', async ({browser, request, managerPage}) => {
        const {page: memberPage, memberId, taskId} = await askSomebody(browser, request, managerPage)

        await memberPage.goto(`/station/inventory/self-check/${taskId}`)
        await expect(memberPage.getByTestId('app-shell')).toBeVisible()
        await answerEverything(memberPage)

        const pieceId = pieceOf(await entryOffering(memberPage, 'self-check-actual-size-piece-'))
        const task = await ownTask(memberPage, taskId)
        const piece = task.assigned.find(item => item.id === pieceId)!
        const actual = task.required
            .find(req => req.inventoryId === piece.inventoryId)!
            .sizes.find(size => size.id !== piece.sizeId)
        expect(actual, 'the gear comes in more than one size').toBeTruthy()

        await memberPage.getByTestId(`self-check-actual-size-piece-${pieceId}`).selectOption(String(actual!.id))
        await expect(memberPage.getByTestId(`self-check-answer-piece-${pieceId}-WRONG_RECORD`)).toHaveClass(
            /ring-primary/,
        )
        await memberPage.getByTestId('self-check-submit').click()
        await expect(memberPage.getByTestId('self-check-submitted')).toBeVisible()

        const submitted = await review(managerPage, taskId)
        const wrong = submitted.rows.find((entry: {row: {itemId: number | null}}) => entry.row.itemId === pieceId)
        expect(wrong.row.answer, 'changing the size says the record is wrong').toBe('WRONG_RECORD')
        expect(wrong.statedSize, 'and carries the size the member actually holds').toBe(actual!.label)

        await managerPage.goto(`/station/inventory/checks/self/${taskId}`)
        await expect(managerPage.getByTestId('review-people')).toBeVisible()
        await expect(managerPage.getByTestId(`review-stated-size-${wrong.row.id}`)).toContainText(actual!.label)

        await managerPage.getByTestId(`review-correct-${wrong.row.id}`).click()
        const source = managerPage.getByTestId('correct-source')
        if (await source.isVisible()) await source.selectOption('NEW')
        await managerPage.getByTestId('correct-confirm').click()
        await expect(managerPage.getByTestId('correct-confirm')).toBeHidden()

        const settled = await review(managerPage, taskId)
        const put = settled.rows.find((entry: {row: {id: number}}) => entry.row.id === wrong.row.id)
        expect(put.item.sizeId, 'the record now says the size the member gave').toBe(actual!.id)
        expect(put.item.assignedTo, 'against the member it was about').toBe(memberId)
        expect(put.row.itemId, 'which is no longer the piece the record had wrong').not.toBe(pieceId)
        await memberPage.context().close()
    })

    /**
     * SFC-5: a broken piece raises the same swap a piece that no longer fits does, saying which of
     * the two it was and keeping the size it already is.
     */
    test('a broken piece raises a swap that says so and keeps its size', async ({browser, request, managerPage}) => {
        const {page: memberPage, taskId} = await askSomebody(browser, request, managerPage)

        await memberPage.goto(`/station/inventory/self-check/${taskId}`)
        await expect(memberPage.getByTestId('app-shell')).toBeVisible()

        const pieceId = pieceOf(await entryOffering(memberPage, 'self-check-broken-piece-'))
        const sizeId = (await ownTask(memberPage, taskId)).assigned.find(item => item.id === pieceId)!.sizeId
        expect(sizeId, 'the piece is one the record gives a size').toBeTruthy()

        await memberPage.getByTestId(`self-check-broken-piece-${pieceId}`).click()
        await expect(memberPage.getByTestId('exchange-cause')).toContainText('kaputt')
        await expect(memberPage.getByTestId('exchange-new-size')).toHaveValue(String(sizeId))
        await memberPage.getByTestId('exchange-submit').click()
        await expect(memberPage.getByTestId(`self-check-broken-piece-${pieceId}`)).toBeDisabled()

        const raised = (await ownTask(memberPage, taskId)) as unknown as {raised: {kind: string; itemId: number}[]}
        expect(
            raised.raised.some(entry => entry.kind === 'EXCHANGE' && entry.itemId === pieceId),
            'the task records that a swap was set going here',
        ).toBeTruthy()

        const exchanges = await managerPage.request.get('/api/v1/exchanges', {headers: await apiHeaders(managerPage)})
        expect(exchanges.ok(), `the station reads its swaps (${await exchanges.text()})`).toBeTruthy()
        const swap = (await exchanges.json()).find((entry: {itemId: number | null}) => entry.itemId === pieceId)
        expect(swap, 'the swap reached the station').toBeTruthy()
        expect(swap.reason, 'and says why it arose').toContain('kaputt')
        expect(swap.newSizeId, 'the same size comes back').toBe(sizeId)
        await memberPage.context().close()
    })

    /**
     * SFC-7: the record has the wrong size, the member puts it right and asks for a swap in the same
     * breath. The swap waits for the checker to take the correction, and starts from the size that
     * was put right rather than the one the record had wrong.
     */
    test('a swap asked for beside a corrected size waits and then carries the size put right',
        async ({browser, request, managerPage}) => {
            const {page: memberPage, taskId} = await askSomebody(browser, request, managerPage)

            await memberPage.goto(`/station/inventory/self-check/${taskId}`)
            await expect(memberPage.getByTestId('app-shell')).toBeVisible()

            const pieceId = pieceOf(
                await entryOfferingBoth(memberPage, 'self-check-broken-piece-', 'self-check-actual-size-piece-'),
            )
            await answerEverything(memberPage)
            const task = await ownTask(memberPage, taskId)
            const piece = task.assigned.find(item => item.id === pieceId)!
            const sizes = task.required.find(req => req.inventoryId === piece.inventoryId)!.sizes
            const actual = sizes.find(size => size.id !== piece.sizeId)
            expect(actual, 'the gear comes in more than one size').toBeTruthy()

            await memberPage.getByTestId(`self-check-actual-size-piece-${pieceId}`).selectOption(String(actual!.id))
            await expect(memberPage.getByTestId(`self-check-timing-piece-${pieceId}`)).toContainText('vorgemerkt')

            await memberPage.getByTestId(`self-check-broken-piece-${pieceId}`).click()
            await expect(memberPage.getByTestId('exchange-new-size')).toHaveValue(String(actual!.id))
            await memberPage.getByTestId('exchange-submit').click()
            await expect(memberPage.getByTestId(`self-check-broken-piece-${pieceId}`)).toBeDisabled()
            await expect(memberPage.getByTestId(`self-check-broken-piece-${pieceId}`)).toContainText('vorgemerkt')

            const held = (await ownTask(memberPage, taskId)) as unknown as {
                raised: {kind: string; state: string; itemId: number}[]
            }
            expect(
                held.raised.some(entry => entry.kind === 'EXCHANGE' && entry.itemId === pieceId
                    && entry.state === 'WAITING'),
                'the swap is written down and waiting',
            ).toBeTruthy()

            const before = await managerPage.request.get('/api/v1/exchanges', {headers: await apiHeaders(managerPage)})
            expect(
                (await before.json()).some((entry: {itemId: number | null}) => entry.itemId === pieceId),
                'and nothing about the piece with the wrong size has reached the station',
            ).toBeFalsy()

            await memberPage.getByTestId('self-check-submit').click()
            await expect(memberPage.getByTestId('self-check-submitted')).toBeVisible()

            const submitted = await review(managerPage, taskId)
            const wrong = submitted.rows.find((entry: {row: {itemId: number | null}}) => entry.row.itemId === pieceId)
            expect(wrong.row.answer, 'changing the size says the record is wrong').toBe('WRONG_RECORD')

            await managerPage.goto(`/station/inventory/checks/self/${taskId}`)
            await expect(managerPage.getByTestId('review-people')).toBeVisible()
            await expect(managerPage.getByTestId('review-waiting').first()).toBeVisible()

            await managerPage.getByTestId(`review-correct-${wrong.row.id}`).click()
            const source = managerPage.getByTestId('correct-source')
            if (await source.isVisible()) await source.selectOption('NEW')
            await managerPage.getByTestId('correct-confirm').click()
            await expect(managerPage.getByTestId('correct-confirm')).toBeHidden()

            const settled = await review(managerPage, taskId)
            const put = settled.rows.find((entry: {row: {id: number}}) => entry.row.id === wrong.row.id)
            expect(put.item.sizeId, 'the record now says the size the member gave').toBe(actual!.id)

            const sent = settled.raised.find(
                (entry: {raised: {kind: string; state: string}}) =>
                    entry.raised.kind === 'EXCHANGE' && entry.raised.state === 'RAISED',
            )
            expect(sent, 'and the swap that waited has gone out').toBeTruthy()

            const after = await managerPage.request.get('/api/v1/exchanges', {headers: await apiHeaders(managerPage)})
            const swap = (await after.json()).find(
                (entry: {id: number}) => entry.id === sent.raised.movementId,
            )
            expect(swap, 'the swap reached the station').toBeTruthy()
            expect(swap.oldSizeId, 'starting from the size that was put right').toBe(actual!.id)
            expect(swap.itemId, 'against the piece the member actually holds').toBe(put.row.itemId)
            await memberPage.context().close()
        })

    /**
     * SFC-8: the counter-check. A line nobody is correcting reports its loss at once, exactly as it
     * did before any of the waiting existed.
     */
    test('a loss on a line nobody is correcting still goes out at once',
        async ({browser, request, managerPage}) => {
            const {page: memberPage, taskId} = await askSomebody(browser, request, managerPage)

            await memberPage.goto(`/station/inventory/self-check/${taskId}`)
            await expect(memberPage.getByTestId('app-shell')).toBeVisible()

            const pieceId = pieceOf(await entryOffering(memberPage, 'self-check-lost-piece-'))
            await expect(memberPage.getByTestId(`self-check-timing-piece-${pieceId}`)).toContainText('sofort')

            await memberPage.getByTestId(`self-check-lost-piece-${pieceId}`).click()
            await memberPage.getByTestId('report-lost-note').fill('Im Zeltlager liegen geblieben')
            await memberPage.getByTestId('report-lost-submit').click()
            await expect(
                memberPage.getByTestId(`self-check-lost-piece-${pieceId}`),
                'a piece the station has written off stops offering to be reported again',
            ).toBeHidden()
            await expect(memberPage.getByTestId(`self-check-entry-piece-${pieceId}`)).toContainText('vermisst')

            const raised = (await ownTask(memberPage, taskId)) as unknown as {
                raised: {kind: string; state: string; itemId: number}[]
            }
            expect(
                raised.raised.some(entry => entry.kind === 'LOSS' && entry.itemId === pieceId
                    && entry.state === 'RAISED'),
                'the loss waited for nobody',
            ).toBeTruthy()

            const piece = await managerPage.request.get(`/api/v1/inventory-items/${pieceId}`, {
                headers: await apiHeaders(managerPage),
            })
            expect(piece.ok(), `the station reads the piece (${await piece.text()})`).toBeTruthy()
            expect((await piece.json()).custody, 'and the piece counts as missing at once').toBe('LOST')
            await memberPage.context().close()
        })

    /**
     * SFC-3: a checker may not sign off a submission they entered themselves, whatever permission
     * they hold. The two names on a check are the point of it.
     */
    test('a checker cannot sign off a submission they entered themselves', async ({managerPage}) => {
        const headers = await apiHeaders(managerPage)
        const memberId = await ownMemberId(managerPage)
        const handed = await managerPage.request.post('/api/v1/self-checks', {
            headers,
            data: {memberIds: [memberId], dueOn: null},
        })
        expect(handed.ok(), `the checker hands themselves a task (${await handed.text()})`).toBeTruthy()
        const tasks = await handed.json()
        expect(tasks.length, 'the checker holds no unfinished task of their own').toBeGreaterThan(0)
        const taskId = tasks[0].id

        const task = await managerPage.request.get(`/api/v1/self-checks/${taskId}`, {headers})
        expect(task.ok(), `the checker reads their own task (${await task.text()})`).toBeTruthy()
        const own = await task.json()
        const piece = own.assigned[0]
        if (piece) {
            const saved = await managerPage.request.put(`/api/v1/self-checks/${taskId}/answers`, {
                headers,
                data: {answers: [{itemId: piece.id, answer: 'HAVE_IT', note: ''}]},
            })
            expect(saved.ok(), `the checker answers their own task (${await saved.text()})`).toBeTruthy()
        }
        await managerPage.request.post(`/api/v1/self-checks/${taskId}/submit`, {headers})

        const read = await review(managerPage, taskId)
        expect(read.mayApprove, 'a checker may not sign off their own gear').toBeFalsy()
        expect(read.approvalRefusal.length, 'and is told why').toBeGreaterThan(0)

        await managerPage.goto(`/station/inventory/checks/self/${taskId}`)
        await expect(managerPage.getByTestId('review-refusal')).toBeVisible()
        await expect(managerPage.locator('[data-testid^="review-take-"]')).toHaveCount(0)

        if (read.rows.length > 0) {
            const refused = await managerPage.request.post(
                `/api/v1/self-check-reviews/${taskId}/rows/${read.rows[0].row.id}/take`,
                {headers},
            )
            expect(refused.status(), 'the endpoint refuses it too, not only the screen').toBe(403)
        }
    })
})
