/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'

test.describe('Profile', () => {
    test('a member sees their own profile', async ({memberPage: page}) => {
        await page.goto('/station/profile')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/profile')
    })

    test('a member sees the equipment they hold on their profile', async ({memberPage: page}) => {
        await page.goto('/station/profile/inventory')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * A station can ask something of one group alone. The question was stored, listed in the
     * configuration and then shown to nobody: the profile fetched every field of the station and
     * decided for itself which applied, and its copy of that rule threw away everything of group
     * scope. Somebody in the group never saw what their group is asked.
     */
    test('somebody in a group is asked what their group is asked', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()

        const mine = await page.request.get('/api/v1/session', {headers}).then(r => r.json())
        const myGroups: {id: number; name: string}[] = mine.groups ?? []
        expect(myGroups.length, 'the acting member is in a group to be asked about').toBeGreaterThan(0)
        const group = myGroups[0]!

        const question = `Nachweis ${stamp}`
        const made = await page.request.post('/api/v1/profile-fields', {
            headers,
            data: {name: question, fieldType: 'TEXT', config: {groupId: group.id}, position: 90, scope: 'GROUP'},
        })
        expect(made.ok(), 'the question was written down').toBeTruthy()
        const fieldId = (await made.json()).id

        try {
            await page.goto('/station/profile')
            await expect(page.getByTestId('app-shell')).toBeVisible()
            await expect(page.getByText(question), 'the group question stands on the profile').toBeVisible()
        } finally {
            await page.request.delete(`/api/v1/profile-fields/${fieldId}`, {headers})
        }
    })

    /** A question asked of a group nobody in it is not put to somebody outside it. */
    test('somebody outside a group is not asked what that group is asked', async ({managerPage: page, memberPage}) => {
        const headers = await apiHeaders(page)
        const stamp = Date.now()

        const groups = await page.request.get('/api/v1/groups', {headers}).then(r => r.json())
        const mine = await memberPage.request.get('/api/v1/session', {headers: await apiHeaders(memberPage)})
            .then(r => r.json())
        const theirs = new Set((mine.groups ?? []).map((g: {id: number}) => g.id))
        const outside = (groups as {id: number}[]).find(g => !theirs.has(g.id))
        expect(outside, 'there is a group they are not in').toBeTruthy()

        const question = `Fremd ${stamp}`
        const made = await page.request.post('/api/v1/profile-fields', {
            headers,
            data: {name: question, fieldType: 'TEXT', config: {groupId: outside!.id}, position: 91, scope: 'GROUP'},
        })
        const fieldId = (await made.json()).id

        try {
            await memberPage.goto('/station/profile')
            await expect(memberPage.getByTestId('app-shell')).toBeVisible()
            await expect(memberPage.getByText(question)).toHaveCount(0)
        } finally {
            await page.request.delete(`/api/v1/profile-fields/${fieldId}`, {headers})
        }
    })
})
