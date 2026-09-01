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
