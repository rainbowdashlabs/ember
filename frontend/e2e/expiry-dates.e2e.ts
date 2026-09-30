/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * A date that runs out: the first aid course, the licence, the certificate for youth work.
 *
 * <p>The stories are about who sees it running out, and where: the member management in its list and
 * the member on their own profile, each before any reminder arrives. The reminders themselves go out
 * on a half-hourly sweep, which a story cannot wait for; the server's own tests cover them.
 */

const EVERY_KIND = ['MEMBER', 'TEAM', 'GUARDIAN', 'MANAGER']

/** A day this many days from today, as a date field stores it. */
function inDays(days: number): string {
    const day = new Date()
    day.setDate(day.getDate() + days)
    return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
}

/** An expiry date asked of every kind of member, so whoever the story reads is asked it. */
async function askExpiryDate(api: APIRequestContext, headers: Record<string, string>, name: string) {
    const created = await api.post('/api/v1/profile-fields', {
        headers,
        data: {name, fieldType: 'EXPIRY_DATE', config: {warnFromDays: 30}, required: false, readonly: false},
    })
    expect(created.ok(), `the station asked "${name}" (${await created.text()})`).toBeTruthy()
    const fieldId = (await created.json()).id as number
    for (const role of EVERY_KIND) {
        const assigned = await api.put(`/api/v1/profile-fields/${fieldId}/assignments`, {
            headers,
            data: {role, position: 0},
        })
        expect(assigned.ok(), `and put it to ${role} (${await assigned.text()})`).toBeTruthy()
    }
    return fieldId
}

/** The member the page is signed in as. */
async function memberIdOf(page: Page): Promise<number> {
    const session = await page.request.get('/api/v1/session', {headers: await apiHeaders(page)})
    expect(session.ok(), `the reader has a session (${await session.text()})`).toBeTruthy()
    return (await session.json()).member.id as number
}

async function answer(
    api: APIRequestContext, headers: Record<string, string>, memberId: number, fieldId: number, day: string,
) {
    const written = await api.put(`/api/v1/station-members/${memberId}/profile`, {
        headers,
        data: {values: [{fieldId, value: JSON.stringify(day)}]},
    })
    expect(written.ok(), `the date was entered (${await written.text()})`).toBeTruthy()
}

test.describe('Expiry dates', () => {
    /**
     * The field dialog offers the expiry date with its own settings, and what was set is what the
     * field keeps.
     */
    test('a manager writes an expiry date with how early it warns', async ({managerPage}) => {
        const name = `Erste Hilfe gültig bis ${test.info().workerIndex}-${Date.now()}`
        const headers = await apiHeaders(managerPage)
        await managerPage.goto('/station/members/config')
        await expect(managerPage.getByTestId('app-shell')).toBeVisible()

        await managerPage.getByTestId('field-add').first().click({timeout: 15000})
        await managerPage.getByTestId('field-name').fill(name)
        await managerPage.getByTestId('field-type').selectOption('EXPIRY_DATE')
        await expect(managerPage.getByTestId('expiry-settings')).toBeVisible()
        await managerPage.getByTestId('expiry-warn-from').fill('90')
        await managerPage.getByTestId('field-save').click()
        await expect(managerPage.getByTestId(`field-row-${name}`)).toBeVisible({timeout: 15000})

        const fields = await managerPage.request.get('/api/v1/profile-fields', {headers}).then(r => r.json())
        const written = fields.find((field: {name: string}) => field.name === name)
        try {
            expect(written.fieldType).toBe('EXPIRY_DATE')
            expect(written.config.warnFromDays).toBe(90)
            expect(written.config.reminderDays).toEqual([30, 7])
        } finally {
            await managerPage.request.delete(`/api/v1/profile-fields/${written.id}`, {headers})
        }
    })

    /**
     * Member management's reminder leads to the member list narrowed to the dates that ran out, and
     * the list says so in words beside the date.
     */
    test('the member list opens on the dates that ran out', async ({managerPage, memberPage}) => {
        const headers = await apiHeaders(managerPage)
        const name = `Atemschutz gültig bis ${test.info().workerIndex}-${Date.now()}`
        const fieldId = await askExpiryDate(managerPage.request, headers, name)
        const memberId = await memberIdOf(memberPage)
        await answer(managerPage.request, headers, memberId, fieldId, inDays(-3))

        try {
            await managerPage.goto(`/station/members/list?field=${fieldId}&state=expiring,expired`)
            const rows = managerPage.getByTestId('member-row')
            await expect(rows, 'only the member whose date ran out is left').toHaveCount(1, {timeout: 15000})
            await expect(rows.first().getByTestId('expiry-state')).toHaveAttribute('data-state', 'EXPIRED')
        } finally {
            await managerPage.request.delete(`/api/v1/profile-fields/${fieldId}`, {headers})
        }
    })

    /** The member sees their own date running out on their profile, before any reminder does. */
    test('a member sees their own date running out', async ({managerPage, memberPage}) => {
        const headers = await apiHeaders(managerPage)
        const name = `Führerschein gültig bis ${test.info().workerIndex}-${Date.now()}`
        const fieldId = await askExpiryDate(managerPage.request, headers, name)
        await answer(managerPage.request, headers, await memberIdOf(memberPage), fieldId, inDays(12))

        try {
            await memberPage.goto('/station/profile')
            const field = memberPage.locator(`[data-field="${name}"]`)
            await expect(field.getByTestId('expiry-state')).toHaveAttribute('data-state', 'EXPIRING', {timeout: 15000})
            await expect(field.getByTestId('expiry-state')).toContainText('12')
        } finally {
            await managerPage.request.delete(`/api/v1/profile-fields/${fieldId}`, {headers})
        }
    })
})
