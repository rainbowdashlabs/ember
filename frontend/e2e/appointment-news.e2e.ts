/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, type Page} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * Announcing an appointment as a news entry.
 *
 * <p>The dangerous half is the second story. An appointment can be kept from most of the station,
 * while a new entry starts with no audience at all and one click from the public page, so the
 * entry written from a restricted appointment has to start restricted rather than open.
 */

/** Writes an appointment and leaves the browser on it, which is where both stories start. */
async function createAppointment(page: Page, name: string): Promise<number> {
    await page.goto('/station/events/new')
    await page.getByPlaceholder('Name des Termins').fill(name)

    const times = page.locator('input[type="datetime-local"]')
    await times.first().fill('2026-12-03T18:00')
    if (await times.count() > 1) await times.nth(1).fill('2026-12-03T20:00')

    await page.getByRole('button', {name: /Speichern|Erstellen/}).last().click()
    await page.waitForURL(/\/station\/events$/)

    // The name is on the page twice: once in the calendar preview, which leads nowhere, and once on
    // the list entry. Only the entry opens the appointment.
    await page.getByTestId('event-entry').filter({hasText: name}).first().click()
    await page.waitForURL(/\/station\/events\/\d+/)
    return Number(/\/station\/events\/(\d+)/.exec(page.url())?.[1])
}

async function announce(page: Page) {
    await page.getByTestId('event-actions-trigger').click()
    await page.getByTestId('event-announce').click()
    await page.waitForURL(/\/station\/news\/create\?/)
}

test.describe('An appointment announced as a news entry', () => {
    /**
     * The draft carries the appointment's name and the evening it is about, which is the retyping
     * this exists to prevent: a weekly appointment announced without a date says nothing.
     */
    test('the appointment opens the news editor on a finished draft', async ({managerPage: page}) => {
        const name = unique('Ankündigung')
        await createAppointment(page, name)

        await announce(page)

        expect(page.url()).toContain('event=')
        await expect(page.getByTestId('announcement-notice')).toBeVisible()
        await expect(page.getByPlaceholder('Titel der Neuigkeit')).toHaveValue(name)
    })

    /**
     * An appointment only part of the station may know about must not become an announcement the
     * whole station reads, and must not be one click from the public page.
     */
    test('a restricted appointment produces a restricted entry', async ({managerPage: page}) => {
        const name = unique('Verdeckt')
        const eventId = await createAppointment(page, name)

        const headers = await apiHeaders(page)
        const empty = {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'}
        const restricted = await page.request.put(`/api/v1/events/${eventId}/restrictions`, {
            headers,
            data: {register: empty, view: {...empty, userTypes: ['TEAM']}},
        })
        expect(restricted.ok()).toBe(true)

        await page.goto(`/station/events/${eventId}`)
        await announce(page)

        await expect(page.getByTestId('announcement-restricted')).toBeVisible()
    })
})
