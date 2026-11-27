/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * Setting up a station happens once for everybody who manages it, and setting up the instance once
 * for everybody who administers it. These check that the answer really lives on the server rather
 * than in the browser that gave it, which is the whole point of sharing it.
 */
test.describe('Onboarding on the shared levels', () => {
    test('a station manager is offered what the station still needs', async ({managerPage: page}) => {
        await page.goto('/station/dashboard/overview')

        await expect(page.getByText('Eure Wache einrichten').first()).toBeVisible()
    })

    /**
     * Reads the lists rather than changing them, since the station's are shared with every other
     * story, and works out from them which steps the dashboard has to show.
     */
    test('a station manager is walked through the station first, three steps at a time',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            type Task = {id: string; state: string; ready: boolean}
            const readyOf = async (level: string) => {
                const response = await page.request.get(`/api/v1/onboarding/${level}`, {headers})
                expect(response.ok(), await response.text()).toBeTruthy()
                const tasks = (await response.json()).tasks as Task[]
                return {
                    ready: tasks.filter(task => task.state === 'OPEN' && task.ready).map(task => task.id),
                    open: tasks.filter(task => task.state === 'OPEN').length,
                }
            }
            await expect(async () => {
                await page.goto('/station/dashboard/overview')
                await expect(page.getByText('Eure Wache einrichten').first()).toBeVisible()
                const station = await readyOf('station')
                const member = await readyOf('member')
                const expected = [...station.ready, ...member.ready].slice(0, 3)

                const shown = page.locator('li[data-testid^="onboarding-task-"]')
                await expect(shown).toHaveCount(expected.length, {timeout: 2_000})
                const ids = await shown.evaluateAll(items => items.map(item => item.getAttribute('data-testid')))
                expect(ids, 'the station takes the free places before the reader\'s own steps')
                    .toEqual(expected.map(id => `onboarding-task-${id}`))

                if (station.ready.length >= 3 && member.open > 0) {
                    await expect(page.getByTestId('onboarding-waiting')).toBeVisible({timeout: 2_000})
                }
                if (station.open + member.open > expected.length) {
                    await expect(page.getByTestId('onboarding-later')).toBeVisible({timeout: 2_000})
                }
            }, 'other stories change the shared station meanwhile, so the page is read again until both agree')
                .toPass({timeout: 30_000})
        })

    test('what one manager settles is settled for the station', async ({managerPage: page, browser}) => {
        const headers = await apiHeaders(page)

        const marked = await page.request.put('/api/v1/onboarding/station/station.memberTypes', {
            headers,
            data: {state: 'SKIPPED'},
        })
        expect(marked.ok(), await marked.text()).toBeTruthy()

        const second = await browser.newContext({storageState: await page.context().storageState()})
        try {
            const other = await second.newPage()
            const response = await other.request.get('/api/v1/onboarding/station', {
                headers: await apiHeaders(other),
            })
            const tasks = (await response.json()).tasks as Array<{key: string; state: string; actorName: string}>
            const settled = tasks.find(task => task.key === 'station.memberTypes')

            expect(settled?.state, 'the second session sees the same answer').toBe('SKIPPED')
            expect(settled?.actorName, 'and who gave it').toBeTruthy()
        } finally {
            await second.close()
        }

        await page.request.put('/api/v1/onboarding/station/station.memberTypes', {headers, data: {state: 'OPEN'}})
    })

    test('an administrator is offered what the instance still needs, own account first',
        async ({adminPage: page}) => {
            const headers = await apiHeaders(page)

            const response = await page.request.get('/api/v1/onboarding/instance', {headers})
            const tasks = (await response.json()).tasks as Array<{key: string}>

            expect(response.ok(), await response.text()).toBeTruthy()
            expect(tasks[0]?.key, 'the reader is asked about their own account first')
                .toBe('instance.ownAccount')
        })

    test('the instance tasks appear in the administration area', async ({adminPage: page}) => {
        await page.goto('/admin/dashboard/overview')

        await expect(page.getByText('Die Instanz einrichten').first()).toBeVisible()
    })

    test('a manager cannot settle the instance', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)

        const response = await page.request.get('/api/v1/onboarding/instance', {headers})

        expect(response.status()).toBe(403)
    })
})
