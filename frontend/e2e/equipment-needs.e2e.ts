/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import type {APIRequestContext} from '@playwright/test'

/** A day far enough out that nothing the demo data seeded is planned on it. */
function inDays(days: number): string {
    return new Date(Date.now() + days * 86400000).toISOString()
}

function dayOf(iso: string): string {
    return iso.slice(0, 10)
}

/** The seeded radio drawer, which holds six blue ones and a case nobody gave a kind to. */
async function blueRadios(request: APIRequestContext, headers: Record<string, string>): Promise<number> {
    const inventories = await request.get('/api/v1/inventories', {headers})
    const drawer = (await inventories.json()).find((entry: {name: string}) => entry.name === 'Handfunkgeräte')
    const arts = await request.get(`/api/v1/inventories/${drawer.id}/arts`, {headers})
    return (await arts.json()).find((art: {name: string}) => art.name === 'Funkgerät blau').id
}

async function appointment(
    request: APIRequestContext,
    headers: Record<string, string>,
    name: string,
    start: string,
): Promise<number> {
    const created = await request.post('/api/v1/events', {
        headers,
        data: {
            name,
            description: 'Was der Termin braucht',
            eventType: 'ONE_TIME',
            startTime: start,
            endTime: new Date(new Date(start).getTime() + 3 * 3600000).toISOString(),
            requiresRegistration: false,
        },
    })
    expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
    return (await created.json()).id
}

test.describe('Appointment equipment', () => {
    /**
     * The whole idea in one walk: an appointment says what it needs, and the panel answers for the
     * evening rather than in general, naming where the pieces would come from.
     */
    test('an appointment says what it needs and the panel answers for that evening',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const start = inDays(21)
            const eventId = await appointment(page.request, headers, `Ausrüstung ${Date.now()}`, start)

            await page.goto(`/station/events/${eventId}/${dayOf(start)}`)
            await expect(page.getByTestId('app-shell')).toBeVisible()
            await page.getByRole('button', {name: 'Ausrüstung'}).click()

            await expect(page.getByTestId('equipment-empty')).toBeVisible()

            await page.getByTestId('equipment-add').click()
            await page.getByTestId('equipment-line-kind').selectOption('art')
            await page.getByTestId('line-target-art').selectOption({label: 'Funkgerät blau (Handfunkgeräte)'})
            await page.getByTestId('line-target-art-quantity').fill('4')
            await page.getByTestId('equipment-line-submit').click()

            await expect(page.getByTestId('equipment-need')).toHaveCount(1)
            await expect(page.getByTestId('equipment-need-split'), 'the origin is part of the answer')
                .toContainText('Eigene:')

            await page.reload()
            await page.getByRole('button', {name: 'Ausrüstung'}).click()
            await expect(page.getByTestId('equipment-need'), 'the line survives a reload').toHaveCount(1)

            await page.request.delete(`/api/v1/events/${eventId}`, {headers})
        })

    /**
     * Two appointments over the same evening may both write down the same six radios. The second one
     * is not refused: the panel reports the over-claim and names the appointment it collides with,
     * which is what makes the conflict something anybody can act on before the day.
     */
    test('two appointments needing the same pieces report the conflict rather than refusing it',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const start = inDays(35)
            const stamp = `${test.info().workerIndex}-${Date.now()}`
            const artId = await blueRadios(page.request, headers)

            const ids: number[] = []
            for (const suffix of ['A', 'B']) {
                ids.push(await appointment(page.request, headers, `Doppelt ${suffix} ${stamp}`, start))
            }

            for (const eventId of ids) {
                const line = await page.request.post(`/api/v1/events/${eventId}/equipment`, {
                    headers,
                    data: {artId, quantity: 6, leadMinutes: 0, trailMinutes: 0},
                })
                expect(line.ok(), `the second appointment is not refused (${await line.text()})`).toBeTruthy()
            }

            await page.goto(`/station/events/${ids[0]}/${dayOf(start)}`)
            await expect(page.getByTestId('app-shell')).toBeVisible()
            await page.getByRole('button', {name: 'Ausrüstung'}).click()
            await expect(page.getByTestId('equipment-need-overclaim'), 'the other appointment is named')
                .toContainText(`Doppelt B ${stamp}`)

            for (const eventId of ids) await page.request.delete(`/api/v1/events/${eventId}`, {headers})
        })

    /**
     * What is missing leads to the partner stations, and the collecting screen knows what it is
     * collecting for rather than being a bare search.
     */
    test('what is missing leads to the collecting screen', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const start = inDays(49)
        const artId = await blueRadios(page.request, headers)
        const eventId = await appointment(page.request, headers, `Leihen ${Date.now()}`, start)

        await page.request.post(`/api/v1/events/${eventId}/equipment`, {
            headers,
            data: {artId, quantity: 20, leadMinutes: 0, trailMinutes: 0},
        })

        await page.goto(`/station/events/${eventId}/${dayOf(start)}`)
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await page.getByRole('button', {name: 'Ausrüstung'}).click()
        await expect(page.getByTestId('equipment-need-missing')).toBeVisible()

        await page.getByTestId('equipment-borrow').click()
        await expect(page.getByTestId('collect-occasion'), 'the screen knows what it is collecting for')
            .toBeVisible()
        await expect(page.getByTestId('partner-offers')).toBeVisible()
        await expect(page.getByTestId('collected-empty')).toBeVisible()

        await page.request.delete(`/api/v1/events/${eventId}`, {headers})
    })

    /**
     * The collecting screen is reached from an appointment, and that is what fixes the evening the
     * request is for. Opened without one it used to offer a button that could never do anything and
     * said nothing about why.
     */
    test('collecting without an evening says so rather than offering a button that cannot act',
        async ({managerPage: page}) => {
            await page.goto('/station/inventory/lending/collect')
            await expect(page.getByTestId('app-shell')).toBeVisible()

            await expect(page.getByTestId('collect-no-date'), 'the reader is told what is missing')
                .toBeVisible()
        })
})
