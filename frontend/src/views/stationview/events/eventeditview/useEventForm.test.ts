/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {useEventForm} from './useEventForm'
import {RestrictionMode, type EventTemplate, type RestrictionAudience, type TemplateDetailResponse} from '@/api/generated/schema'

vi.mock('@/api', () => ({events: {}}))

const nobody: RestrictionAudience = {groupIds: [], memberIds: [], mode: RestrictionMode.AND, tagIds: [], userTypes: []}

/** A template with nothing set, so a test names only the setting it is about. */
function templateDetail(overrides: Partial<EventTemplate> = {}): TemplateDetailResponse {
    return {
        fields: [],
        registrationFields: [],
        reminderDays: [],
        restriction: {register: nobody, view: nobody},
        template: {
            attendanceTemplateId: null,
            categoryId: null,
            description: null,
            eventType: null,
            id: 4,
            name: 'Übungsdienst',
            registrationDeadlineOffset: null,
            registrationLimit: null,
            requiresConfirmation: null,
            requiresRegistration: null,
            restrictionMode: null,
            stationId: '00000000-0000-0000-0000-000000000000',
            title: null,
            viewRestrictionMode: RestrictionMode.AND,
            ...overrides,
        },
    }
}

describe('useEventForm.applyTemplate', () => {
    it('takes the participant limit from the template', () => {
        const form = useEventForm()

        form.applyTemplate(templateDetail({requiresRegistration: true, registrationLimit: 12}))

        expect(form.state.registrationLimit).toBe(12)
    })

    it('keeps the limit already entered when the template sets none', () => {
        const form = useEventForm()
        form.state.registrationLimit = 8

        form.applyTemplate(templateDetail())

        expect(form.state.registrationLimit).toBe(8)
    })
})
