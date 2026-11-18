/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import {unique} from './fixtures/unique'

test.describe('Places', () => {
    /**
     * A place an appointment names is pinned for the station, the way the pin dialog stores it, and the
     * appointment page then shows it on a small map with a link to OpenStreetMap; once the pin is taken off, the
     * page shows the place as text only.
     *
     * <p>The place text is the story's own, so no other appointment of the seeded station carries its pin. No
     * address search is asked: the pin is placed by hand, as on an instance without one.
     */
    test('a pinned place shows on the appointment page until the pin is taken off', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const place = unique('Sammelplatz')
        const start = new Date(Date.now() + 5 * 86400000)

        const created = await page.request.post('/api/v1/events', {
            headers,
            data: {
                name: unique('Ausflug'),
                eventType: 'ONE_TIME',
                startTime: start.toISOString(),
                endTime: new Date(start.getTime() + 3600000).toISOString(),
            },
        })
        expect(created.ok(), `the appointment is made (${await created.text()})`).toBeTruthy()
        const eventId = (await created.json()).id
        const fields = await page.request.put(`/api/v1/events/${eventId}/fields`, {
            headers,
            data: {fields: [{name: 'Treffpunkt', fieldType: 'LOCATION', value: place, isPublic: false, overview: false}]},
        })
        expect(fields.ok(), `the place field is set (${await fields.text()})`).toBeTruthy()

        const pinned = await page.request.put('/api/v1/station/places', {
            headers,
            data: {text: place, latitude: 52.52, longitude: 13.405, precision: null},
        })
        expect(pinned.ok(), `the place is pinned (${await pinned.text()})`).toBeTruthy()

        await page.goto(`/station/events/${eventId}`)
        await expect(page.getByText(place).first()).toBeVisible()
        await expect(page.getByTestId('place-map')).toBeVisible()
        await expect(page.getByRole('link', {name: 'In OpenStreetMap öffnen'}))
            .toHaveAttribute('href', /mlat=52\.52&mlon=13\.405/)

        const removed = await page.request.delete('/api/v1/station/places', {headers, params: {text: place}})
        expect(removed.status()).toBe(204)
        await page.reload()
        await expect(page.getByText(place).first()).toBeVisible()
        await expect(page.getByTestId('place-map')).toHaveCount(0)
    })
})
