/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import {openFormSection} from './fixtures/formSection'
import {unique} from './fixtures/unique'

/**
 * The event group editor and what it hands to the appointments of the group.
 */
test.describe('Event groups', () => {
    /**
     * A field of the event group's attendance sheet is offered in the group's fields and, taken over,
     * stays tied to the sheet once the group is saved, so every appointment of the group fills it in
     * on its own sheet.
     */
    test('a field of the attendance sheet is taken into the event group with its tie', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const template = await page.request
            .post('/api/v1/attendance/templates', {headers, data: {name: unique('Lehrgangsbogen')}})
            .then(response => response.json()) as {id: number}
        const field = await page.request
            .post(`/api/v1/attendance/templates/${template.id}/fields`, {
                headers,
                data: {name: 'Ausbilder', fieldType: 'MEMBER_LIST', config: {required: false}, position: 0},
            })
            .then(response => response.json())
            .then((fields: {id: number; name: string}[]) => fields.find(entry => entry.name === 'Ausbilder')!)
        const created = await page.request.post('/api/v1/events/groups', {
            headers,
            data: {
                details: {
                    name: unique('Grundlehrgang'), description: null, registrationLimit: null, registrationDeadline: null,
                    requiresConfirmation: false, color: null, minRegistrations: null, thresholdDays: null,
                    partRegistrationLimit: null, queueOrder: null,
                },
                sets: [{id: null, name: 'Lehrgangstag', minimum: 1, maximum: null}],
            },
        })
        expect(created.ok(), `the organiser made an event group (${await created.text()})`).toBeTruthy()
        const groupId = (await created.json() as {eventGroup: {id: number}}).eventGroup.id

        try {
            await page.goto(`/station/events/groups/${groupId}/edit`)
            await page.getByTestId('event-group-attendance-template').selectOption(String(template.id))
            await openFormSection(page, 'fields')
            await page.getByTestId(`take-attendance-field-${field.id}`).click()
            await page.getByTestId('event-group-save').click()

            await expect.poll(async () => {
                const detail = await page.request.get(`/api/v1/events/groups/${groupId}`, {headers})
                const fields = (await detail.json() as {fields: {name: string; attendanceFieldId: number | null}[]}).fields
                return fields.find(entry => entry.name === 'Ausbilder')?.attendanceFieldId ?? null
            }, {message: 'the saved field stays tied to the sheet'}).toBe(field.id)

            await page.reload()
            await openFormSection(page, 'fields')
            await expect(page.getByTestId(`take-attendance-field-${field.id}`)).toHaveCount(0)
        } finally {
            await page.request.delete(`/api/v1/events/groups/${groupId}`, {headers})
            await page.request.delete(`/api/v1/attendance/templates/${template.id}`, {headers})
        }
    })
})
