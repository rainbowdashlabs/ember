/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders, type Page} from './fixtures/auth'

async function makeAppointment(page: Page, headers: Record<string, string>, name: string, start: Date) {
    const created = await page.request.post('/api/v1/events', {
        headers,
        data: {
            name,
            eventType: 'ONE_TIME',
            startTime: start.toISOString(),
            endTime: new Date(start.getTime() + 90 * 60000).toISOString(),
        },
    })
    expect(created.ok(), `the organiser made an appointment (${await created.text()})`).toBeTruthy()
    return (await created.json()).id as number
}

/**
 * When an appointment is, wherever it is read.
 *
 * <p>The list of what is coming up and the page of the single appointment take their times from the
 * same stored moment by two different routes, and a reader who is told one time on the list and
 * another on the page has no way of telling which of them to turn up at.
 */
test.describe('The time of an appointment', () => {
    test('reads the same on the list of what is coming up and on the appointment itself',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const start = new Date(Date.now() + 9 * 86400000)
            start.setHours(19, 30, 0, 0)
            const name = `Uhrzeitprobe ${test.info().workerIndex}-${Date.now()}`
            const eventId = await makeAppointment(page, headers, name, start)

            await page.goto('/station/events/upcoming')
            await page.getByPlaceholder('Titel, Beschreibung oder Feldinhalt...').fill(name)
            const row = page.locator(`[data-testid="upcoming-event"][data-event="${eventId}"]`).first()
            await expect(row).toBeVisible()
            const onTheList = await row.getByTestId('upcoming-event-when').innerText()

            await page.goto(`/station/events/${eventId}`)
            const onThePage = await page.getByTestId('event-start').innerText()

            expect(onTheList, 'the list says the time it was given').toContain('19:30')
            expect(onThePage, 'and so does the appointment itself').toContain('19:30')
            expect(await page.getByTestId('event-end').innerText(), 'the end too').toContain('21:00')

            await page.request.delete(`/api/v1/events/${eventId}`, {headers})
        })

    /**
     * An appointment half an hour after midnight has already turned over in London, and the page
     * used to take its day from there while taking its clock from the reader. It then wrote the
     * evening before over half past midnight, which is a day out.
     */
    test('names the day it falls on even when that day has not started in London yet',
        async ({managerPage: page}) => {
            const headers = await apiHeaders(page)
            const start = new Date(Date.now() + 9 * 86400000)
            start.setHours(0, 30, 0, 0)
            const name = `Mitternachtsprobe ${test.info().workerIndex}-${Date.now()}`
            const eventId = await makeAppointment(page, headers, name, start)

            await page.goto(`/station/events/${eventId}`)
            const shown = await page.getByTestId('event-start').innerText()

            const expected = start.toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
            expect(shown, 'the day and the clock are read off one and the same moment').toContain(expected)
            expect(shown).toContain('00:30')

            await page.request.delete(`/api/v1/events/${eventId}`, {headers})
        })
})
