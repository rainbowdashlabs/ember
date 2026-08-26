/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import type {Page} from '@playwright/test'

/**
 * The task tour runs over the real pages, so it is the one part of Ember that cannot be checked by
 * mounting a component: whether the ring lands on the button that is there today only shows in a
 * real session walking the real screens.
 *
 * The demo member is shared with every other story, and what somebody said about a task outlives
 * the run that said it. Each story therefore puts the task it works on back where it found it,
 * rather than leaving the next reader with a list somebody else has already emptied.
 */

const TASK = 'member.absence'

async function setTaskState(page: Page, state: 'OPEN' | 'DONE' | 'SKIPPED') {
    const response = await page.request.put(`/api/v1/onboarding/member/${TASK}`, {
        headers: await apiHeaders(page),
        data: {state},
    })
    expect(response.ok(), await response.text()).toBeTruthy()
}

test.describe('Onboarding tasks', () => {
    test('a member is offered their first steps and can begin one', async ({memberPage: page}) => {
        await setTaskState(page, 'OPEN')
        await page.goto('/station/dashboard/overview')

        const task = page.getByTestId(`onboarding-task-${TASK}`)
        await expect(task).toBeVisible()

        await task.getByRole('button', {name: 'Los geht’s'}).click()

        await expect(page.getByRole('button', {name: 'Aufgabe überspringen'})).toBeVisible()
    })

    test('beginning a task points at the way there rather than taking it', async ({memberPage: page}) => {
        await setTaskState(page, 'OPEN')
        await page.goto('/station/dashboard/overview')

        await page.getByTestId(`onboarding-task-${TASK}`).getByRole('button', {name: 'Los geht’s'}).click()

        await expect(page).toHaveURL(/\/station\/dashboard\/overview/)
        await expect(page.getByRole('button', {name: 'Aufgabe überspringen'})).toBeVisible()
        await expect(page.getByText('Klapp das auf')).toBeVisible()
    })

    test('a skipped task is gone after a reload and can be taken up again', async ({memberPage: page}) => {
        await setTaskState(page, 'OPEN')
        await page.goto('/station/dashboard/overview')

        await page.getByTestId(`onboarding-task-${TASK}`).getByRole('button', {name: 'Überspringen'}).click()
        await page.reload()

        await expect(page.getByTestId(`onboarding-task-${TASK}`)).toHaveCount(0)
        await page.getByRole('button', {name: 'Wieder aufnehmen'}).first().click()
        await expect(page.getByTestId(`onboarding-task-${TASK}`)).toBeVisible()

        await setTaskState(page, 'OPEN')
    })

    test('a task Ember reads for itself refuses to be ticked off', async ({memberPage: page}) => {
        const headers = await apiHeaders(page)

        const response = await page.request.put('/api/v1/onboarding/member/member.profile', {
            headers,
            data: {state: 'DONE'},
        })

        expect(response.status(), 'a derived task cannot be declared done').toBe(400)
    })

    test('a task Ember cannot see is ticked off and stays that way', async ({memberPage: page}) => {
        const headers = await apiHeaders(page)

        const marked = await page.request.put('/api/v1/onboarding/member/member.bookmark', {
            headers,
            data: {state: 'DONE'},
        })
        expect(marked.ok(), await marked.text()).toBeTruthy()

        const after = await page.request.get('/api/v1/onboarding/member', {headers})
        const tasks = (await after.json()).tasks as Array<{key: string; state: string}>
        expect(tasks.find(task => task.key === 'member.bookmark')?.state).toBe('DONE')

        await page.request.put('/api/v1/onboarding/member/member.bookmark', {headers, data: {state: 'OPEN'}})
    })

    test('the tour offers nothing that belongs to somebody else', async ({memberPage: page}) => {
        const headers = await apiHeaders(page)

        const response = await page.request.get('/api/v1/onboarding/station', {headers})

        expect(response.status(), 'a member is not asked to set up the station').toBe(403)
    })
})
