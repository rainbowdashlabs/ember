/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import type {Page} from './fixtures/auth'

/**
 * The problem log kept in the database.
 *
 * Problems are made the way an operator meets them. A fault: an address behind a real route that finds
 * nothing, here resolving a problem that is not there. A refusal: the same route asked with an id that is
 * no number. The stories hold that a muted fault happening again stays out of the open list while its
 * count goes on, that refusals are listed apart from faults, and that the page is the instance
 * administration's alone.
 *
 * A problem a story works with is deleted first where an earlier attempt left it behind, so a retry
 * starts from where a first attempt did.
 */
const PAGE = '/admin/monitoring/problems'

interface Problem {
    id: number
    state: string
    count: number
    distinctMessages: string[]
}

async function call(page: Page, method: 'get' | 'post' | 'delete', path: string) {
    return page.request[method](`/api/v1${path}`, {headers: await apiHeaders(page)})
}

/** Asks to resolve a problem that is not there, which the problem log records under the route. */
async function missAProblem(page: Page): Promise<string> {
    const missing = String(900_000_000 + Math.floor(Math.random() * 99_999_999))
    expect((await call(page, 'post', `/admin/problems/${missing}/resolve`)).status()).toBe(404)
    return missing
}

async function listed(page: Page, query: string): Promise<Problem[]> {
    const response = await call(page, 'get', `/admin/problems?${query}`)
    expect(response.ok()).toBe(true)
    return await response.json() as Problem[]
}

/** The problem a message was counted on, once the log has written what it gathered. */
async function problemWith(page: Page, text: string, query: string): Promise<Problem> {
    let found: Problem | undefined
    await expect.poll(async () => {
        found = (await listed(page, query)).find(p => p.distinctMessages.some(m => m.includes(text)))
        return found !== undefined
    }, {timeout: 20_000}).toBe(true)
    return found!
}

/** What every missed problem's messages start with, whichever id was asked for. */
const MISS = /^404 on POST \/api\/v1\/admin\/problems\/\d+\/resolve/

/**
 * The missed-problem fault, open and with room for more messages. Whatever an earlier attempt left of
 * it is found by its route rather than by an id, since a full problem no longer stores new messages,
 * and deleted, so the story starts from a problem made afresh.
 */
async function anOpenMiss(page: Page): Promise<Problem> {
    const left = (await listed(page, 'state=all')).find(p => MISS.test(p.distinctMessages[0] ?? ''))
    if (left) expect((await call(page, 'delete', `/admin/problems/${left.id}`)).status()).toBe(204)
    return await problemWith(page, await missAProblem(page), 'state=OPEN')
}

test.describe('Problem log', () => {
    test('a muted problem that happens again stays out of the open list', async ({adminPage: page}) => {
        const problem = await anOpenMiss(page)

        await page.goto(PAGE)
        await page.getByTestId(`problem-mute-${problem.id}`).click()
        await expect(page.getByTestId(`problem-${problem.id}`)).toHaveCount(0)

        const muted = await problemWith(page, await missAProblem(page), 'state=MUTED')
        expect(muted.id).toBe(problem.id)
        expect(muted.count).toBeGreaterThan(problem.count)
        expect((await listed(page, 'state=OPEN')).some(p => p.id === problem.id)).toBe(false)

        await page.getByTestId('problems-state-MUTED').click()
        await expect(page.getByTestId(`problem-${problem.id}`)).toBeVisible()
    })

    test('a refusal is listed apart from the faults', async ({adminPage: page}) => {
        const response = await call(page, 'post', '/admin/problems/not-a-number/resolve')
        expect(response.status()).toBe(400)

        const refusal = await problemWith(page, 'not-a-number', 'state=all&refusals=true')
        expect((await listed(page, 'state=all')).some(p => p.id === refusal.id)).toBe(false)

        await page.goto(PAGE)
        await page.getByTestId('problems-refusals').click()
        await page.getByTestId('problems-state-all').click()
        await expect(page.getByText(/Ablehnungen sind Anfragen/)).toBeVisible()
        await expect(page.getByTestId(`problem-${refusal.id}`)).toBeVisible()
    })

    test('a station manager reaches none of it', async ({managerPage: page}) => {
        await page.goto(PAGE)

        await expect(page.getByTestId('problems-refusals')).toHaveCount(0)
        const response = await call(page, 'get', '/admin/problems')
        expect(response.status()).toBe(403)
    })
})
